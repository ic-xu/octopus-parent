package io.octopus.broker;

import io.handler.codec.mqtt.MqttPublishMessage;
import io.octopus.broker.subscriptions.Subscription;
import io.octopus.broker.subscriptions.Topic;
import io.netty.buffer.ByteBuf;
import io.handler.codec.mqtt.*;
import io.netty.util.ReferenceCountUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class Session {

    private static final Logger LOGGER = LoggerFactory.getLogger(Session.class);
    private static final int FLIGHT_BEFORE_RESEND_MS = 5_000;
    private static int INFLICT_WINDOW_SIZE = 10;

    static class InFlightPacket implements Delayed {

        final int packetId;
        private final long startTime;

        InFlightPacket(int packetId, long delayInMilliseconds) {
            this.packetId = packetId;
            this.startTime = System.currentTimeMillis() + delayInMilliseconds;
        }

        @Override
        public long getDelay(TimeUnit unit) {
            long diff = startTime - System.currentTimeMillis();
            return unit.convert(diff, TimeUnit.MILLISECONDS);
        }

        @Override
        public int compareTo(Delayed o) {
            if ((this.startTime - ((InFlightPacket) o).startTime) == 0) {
                return 0;
            }
            if ((this.startTime - ((InFlightPacket) o).startTime) > 0) {
                return 1;
            } else {
                return -1;
            }
        }
    }

    enum SessionStatus {
        CONNECTED, CONNECTING, DISCONNECTING, DISCONNECTED
    }

    static final class Will {

        final String topic;
        final ByteBuf payload;
        final MqttQoS qos;
        final boolean retained;

        Will(String topic, ByteBuf payload, MqttQoS qos, boolean retained) {
            this.topic = topic;
            this.payload = payload;
            this.qos = qos;
            this.retained = retained;
        }
    }

    private final String clientId;
    private boolean clean;
    private Will will;
    private final Queue<SessionRegistry.EnqueuedMessage> sessionQueue;
    private final AtomicReference<SessionStatus> status = new AtomicReference<>(SessionStatus.DISCONNECTED);
    private MqttConnection mqttConnection;
    private int mqttConnectionVersion;

    //subscriptions save session`s subscriptions，
    private final Set<Subscription> subscriptions = new HashSet<>();
    private final Map<Integer, SessionRegistry.EnqueuedMessage> inflictWindow = new ConcurrentHashMap<>();
    private final DelayQueue<InFlightPacket> inflictTimeouts = new DelayQueue<>();
    //    private final LinkedBlockingDeque<MqttCustomerMessage> mqttCustomerMessagesQueue = new LinkedBlockingDeque<>();
    private final Map<Integer, MqttPublishMessage> qos2Receiving = new ConcurrentHashMap<>();
    private final AtomicInteger inflictSlots = new AtomicInteger(INFLICT_WINDOW_SIZE); // this should be configurable
    private InetSocketAddress udpInetSocketAddress;

    public synchronized void addInflictWindow(Map<Integer, SessionRegistry.EnqueuedMessage> inflightWindows) {
        for (Integer packetId : inflightWindows.keySet()) {
            inflictTimeouts.add(new InFlightPacket(packetId, FLIGHT_BEFORE_RESEND_MS));
            inflictSlots.decrementAndGet();
        }
        inflictWindow.putAll(inflightWindows);
    }


    public Map<Integer, SessionRegistry.EnqueuedMessage> getInflictWindow() {
        return inflictWindow;
    }


    Session(String clientId, boolean clean, Will will, Queue<SessionRegistry.EnqueuedMessage> sessionQueue,
            Integer receiveMaximum, int clientVersion) {
        this(clientId, clean, sessionQueue, receiveMaximum, clientVersion);
        this.will = will;
        this.mqttConnectionVersion = clientVersion;
    }

    Session(String clientId, boolean clean, Queue<SessionRegistry.EnqueuedMessage> sessionQueue, Integer receiveMaximum, int clientVersion) {
        this.clientId = clientId;
        this.clean = clean;
        INFLICT_WINDOW_SIZE = receiveMaximum;
        this.sessionQueue = sessionQueue;
        this.mqttConnectionVersion = clientVersion;
    }

    void update(boolean clean, Will will) {
        this.clean = clean;
        this.will = will;
    }

    void markConnecting() {
        assignState(SessionStatus.DISCONNECTED, SessionStatus.CONNECTING);
    }

    boolean completeConnection() {
        return assignState(Session.SessionStatus.CONNECTING, Session.SessionStatus.CONNECTED);
    }

    void bind(MqttConnection mqttConnection) {
        this.mqttConnection = mqttConnection;
    }

    public void bindUdpInetSocketAddress(InetSocketAddress inetSocketAddress) {
        this.udpInetSocketAddress = inetSocketAddress;
    }

    public InetSocketAddress getUdpInetSocketAddress() {
        return this.udpInetSocketAddress;
    }

    public boolean disconnected() {
        return status.get() == SessionStatus.DISCONNECTED;
    }

    public boolean connected() {
        return status.get() == SessionStatus.CONNECTED;
    }

    public String getClientID() {
        return clientId;
    }

    public List<Subscription> getSubscriptions() {
        return new ArrayList<>(subscriptions);
    }

    public void addSubscriptions(List<Subscription> newSubscriptions) {
        subscriptions.addAll(newSubscriptions);
    }

    //TODO 这里应该要设置前缀匹配原则
    public void unSubscriptions(List<String> topic) {

        ArrayList<Subscription> remove = new ArrayList<>();
        for (Subscription subscription : subscriptions) {
            if (topic.contains(subscription.getTopicFilter().getValue())) {
                remove.add(subscription);
            }
        }
        remove.forEach(subscriptions::remove);
    }

    public boolean hasWill() {
        return will != null;
    }

    public Will getWill() {
        return will;
    }

    boolean assignState(SessionStatus expected, SessionStatus newState) {
        return status.compareAndSet(expected, newState);
    }

    public void closeImmediately() {
        mqttConnection.dropConnection();
    }

    public void disconnect() {
        final boolean res = assignState(SessionStatus.CONNECTED, SessionStatus.DISCONNECTING);
        if (!res) {
            LOGGER.info("this status is SessionStatus.DISCONNECTING");
            return;
        }

        mqttConnection = null;
        will = null;
        assignState(SessionStatus.DISCONNECTING, SessionStatus.DISCONNECTED);
    }

    boolean isClean() {
        return clean;
    }

    /**
     * @param packetId id
     */
    public void processPubRec(int packetId) {
        SessionRegistry.EnqueuedMessage removeMsg = inflictWindow.remove(packetId);
        if (removeMsg == null) {
            LOGGER.warn("Received a pubRec with not matching packetId");
            return;
        }
        if (removeMsg instanceof SessionRegistry.PubRelMarker) {
            LOGGER.info("Received a pubRec for packetId that was already moved in second step of Qos2");
            return;
        }

        removeMsg.release();
        inflictSlots.incrementAndGet();
        if (canSkipQueue()) {
            inflictSlots.decrementAndGet();
            inflictWindow.put(packetId, new SessionRegistry.PubRelMarker());
            inflictTimeouts.add(new InFlightPacket(packetId, FLIGHT_BEFORE_RESEND_MS));
            MqttMessage pubRel = MqttConnection.pubRel(packetId);
            mqttConnection.sendIfWritableElseDrop(pubRel);

            drainQueueToConnection();
        } else {
            sessionQueue.add(new SessionRegistry.PubRelMarker());
        }
    }

    public void processPubComp(int messageID) {
        SessionRegistry.EnqueuedMessage removeMsg = inflictWindow.remove(messageID);
        if (removeMsg == null) {
            LOGGER.warn("Received a PUBCOMP with not matching packetId");
            return;
        }

        removeMsg.release();
        inflictSlots.incrementAndGet();
        drainQueueToConnection();

        // TODO notify the interceptor  final InterceptAcknowledgedMessage interceptAckMsg = new InterceptAcknowledgedMessage(inflightMsg, topic, username, messageID);

//                m_interceptor.notifyMessageAcknowledged(interceptAckMsg);
    }

    public void sendPublishOnSessionAtQos(Topic topic, MqttQoS qos, ByteBuf payload) {
        switch (qos) {
            case AT_MOST_ONCE:
                if (connected()) {
                    mqttConnection.sendPublishNotRetainedQos0(topic, qos, payload);
                } else {
                    payload.release();
                }
                break;
            //这里发布的时候两个使用同样的处理方法即可
            case AT_LEAST_ONCE:
                sendPublishQos1(topic, qos, payload);
                break;
            case EXACTLY_ONCE:
                sendPublishQos2(topic, qos, payload);
//                sendPublish(topic, qos, payload);
                break;
            case FAILURE:
                //出错之后也要回收调
                payload.release();
                LOGGER.error("Not admissible");
        }
    }

    private void sendPublishQos1(Topic topic, MqttQoS qos, ByteBuf payload) {
        if (!connected() && isClean()) {
            //pushing messages to disconnected not clean session
            payload.release();
            return;
        }

        if (canSkipQueue()) {
            inflictSlots.decrementAndGet();
            int packetId = mqttConnection.nextPacketId();
            SessionRegistry.EnqueuedMessage old = inflictWindow.put(packetId, new SessionRegistry.PublishedMessage(topic, qos, payload.retainedDuplicate()));
//            payload.retain();
//            SessionRegistry.EnqueuedMessage old = inflightWindow.put(packetId, new SessionRegistry.PublishedMessage(topic, qos, payload));
            // If there already was something, release it.
            if (old != null) {
                old.release();
                inflictSlots.incrementAndGet();
            }
            inflictTimeouts.add(new InFlightPacket(packetId, FLIGHT_BEFORE_RESEND_MS));

            MqttPublishMessage publishMsg = MqttConnection.notRetainedPublishWithMessageId(topic.toString(), qos,
                    payload, packetId);
            mqttConnection.sendPublish(publishMsg);
            LOGGER.debug("Write direct to the peer, inflight slots: {}", inflictSlots.get());
            if (inflictSlots.get() == 0) {
                mqttConnection.flush();
            }
            // TODO drainQueueToConnection();?
        } else {
            final SessionRegistry.PublishedMessage msg = new SessionRegistry.PublishedMessage(topic, qos, payload);
            msg.retain();
            sessionQueue.add(msg);
        }
    }

    //2是加上自定以的版本，可以接搜自定义消息
    public void sendCustomerMessage(MqttMessage mqttMessage) {
        if (mqttConnectionVersion == 2)
            mqttConnection.sendIfWritableElseDrop(mqttMessage);
    }

    private void sendPublishQos2(Topic topic, MqttQoS qos, ByteBuf payload) {
        if (canSkipQueue()) {
            inflictSlots.decrementAndGet();
            int packetId = mqttConnection.nextPacketId();
//            ByteBuf byteBuf = Unpooled.copiedBuffer(payload);
            inflictWindow.put(packetId, new SessionRegistry.PublishedMessage(topic, qos, payload));
            inflictTimeouts.add(new InFlightPacket(packetId, FLIGHT_BEFORE_RESEND_MS));

            //增加一次引用
            payload.retain();
            MqttPublishMessage publishMsg = MqttConnection.notRetainedPublishWithMessageId(topic.toString(), qos,
                    payload, packetId);
            mqttConnection.sendPublish(publishMsg);

            drainQueueToConnection();
        } else {
            final SessionRegistry.PublishedMessage msg = new SessionRegistry.PublishedMessage(topic, qos, payload);
            // Adding to a queue, retain.
            msg.retain();
            sessionQueue.add(msg);
        }
    }


    private void sendPublish(Topic topic, MqttQoS qos, ByteBuf payload) {
        final SessionRegistry.PublishedMessage msg = new SessionRegistry.PublishedMessage(topic, qos, payload);
        sessionQueue.add(msg);
        drainQueueToConnection();
    }

    private boolean canSkipQueue() {
        return sessionQueue.isEmpty() &&
                inflictSlots.get() > 0 &&
                connected() &&
                mqttConnection.channel.isWritable();
    }

    private boolean inflighHasSlotsAndConnectionIsUp() {
        return inflictSlots.get() > 0 &&
                connected() &&
                mqttConnection.channel.isWritable();
    }

    void pubAckReceived(int ackPacketId) {
        // TODO remain to invoke in somehow m_interceptor.notifyMessageAcknowledged
        SessionRegistry.EnqueuedMessage removeMsg = inflictWindow.remove(ackPacketId);
        if (removeMsg == null) {
            LOGGER.warn("Received a pubAck with not matching packetId");
            return;
        }
        removeMsg.release();
        inflictSlots.incrementAndGet();
        for (InFlightPacket next : inflictTimeouts) {
            if (next.packetId == ackPacketId) {
                inflictTimeouts.remove(next);
                break;
            }
        }
        drainQueueToConnection();
    }

    public void flushAllQueuedMessages() {
        drainQueueToConnection();
    }

    public void resendInflictNotAcked() {
        if (inflictTimeouts.size() == 0) {
            for (Integer msgId : inflictWindow.keySet()) {
                SessionRegistry.EnqueuedMessage enqueuedMessage = inflictWindow.get(msgId);
                if (enqueuedMessage instanceof SessionRegistry.PublishedMessage) {
                    SessionRegistry.EnqueuedMessage remove = inflictWindow.remove(msgId);
                    SessionRegistry.PublishedMessage message = (SessionRegistry.PublishedMessage) remove;
                    sendPublishOnSessionAtQos(message.topic, message.publishingQos, message.payload);
                }
            }
        } else {
            Collection<InFlightPacket> expired = new ArrayList<>(INFLICT_WINDOW_SIZE);
            inflictTimeouts.drainTo(expired);
            debugLogPacketIds(expired);
            for (InFlightPacket notAckPacketId : expired) {
                if (inflictWindow.containsKey(notAckPacketId.packetId)) {
                    final SessionRegistry.PublishedMessage msg =
                            (SessionRegistry.PublishedMessage) inflictWindow.get(notAckPacketId.packetId);
                    if (null == msg) {
                        // Already acked...
                        continue;
                    }
                    final Topic topic = msg.topic;
                    final MqttQoS qos = msg.publishingQos;
                    final ByteBuf payload = msg.payload;
//                    final ByteBuf copiedPayload = payload.retainedDuplicate();
//                ByteBuf byteBuf = Unpooled.copiedBuffer(payload);
                    MqttPublishMessage publishMsg = publishNotRetainedDuplicated(notAckPacketId, topic, qos, payload);
                    inflictTimeouts.add(new InFlightPacket(notAckPacketId.packetId, FLIGHT_BEFORE_RESEND_MS));
                    mqttConnection.sendPublish(publishMsg);
                }
            }
        }
    }

//    public void resendInflightNotAcked() {
//        InFlightPacket notAckPacketId = inflightTimeouts.peek();
//        if (notAckPacketId != null) {
//            if (inflightWindow.containsKey(notAckPacketId.packetId)) {
//                final SessionRegistry.PublishedMessage msg =
//                    (SessionRegistry.PublishedMessage) inflightWindow.get(notAckPacketId.packetId);
//                final Topic topic = msg.topic;
//                final MqttQoS qos = msg.publishingQos;
//                final ByteBuf payload = msg.payload;
//                final ByteBuf copiedPayload = payload.retainedDuplicate();
//                MqttPublishMessage publishMsg = publishNotRetainedDuplicated(notAckPacketId, topic, qos, copiedPayload);
//                mqttConnection.sendPublish(publishMsg);
//            }
//        }


//            while (notAckPacketId != null) {
//                if (inflightWindow.containsKey(notAckPacketId.packetId)) {
//                    final SessionRegistry.PublishedMessage msg =
//                        (SessionRegistry.PublishedMessage) inflightWindow.get(notAckPacketId.packetId);
//                    sessionQueue.add(msg);
//                    notAckPacketId = inflightTimeouts.poll();
//                }
//            }
//    }


    private void debugLogPacketIds(Collection<InFlightPacket> expired) {
        if (!LOGGER.isDebugEnabled() || expired.isEmpty()) {
            return;
        }

        StringBuilder sb = new StringBuilder();
        for (InFlightPacket packet : expired) {
            sb.append(packet.packetId).append(", ");
        }
        LOGGER.debug("Resending {} in flight packets [{}]", expired.size(), sb);
    }

    private MqttPublishMessage publishNotRetainedDuplicated(InFlightPacket notAckPacketId, Topic topic, MqttQoS
            qos,
                                                            ByteBuf payload) {
        MqttFixedHeader fixedHeader = new MqttFixedHeader(MqttMessageType.PUBLISH, true, qos, false, 0);
        MqttPublishVariableHeader varHeader = new MqttPublishVariableHeader(topic.toString(), notAckPacketId.packetId);
        return new MqttPublishMessage(fixedHeader, varHeader, payload);
    }

    private void drainQueueToConnection() {
        resendInflictNotAcked();
        // consume the queue
        while (!sessionQueue.isEmpty() && inflighHasSlotsAndConnectionIsUp()) {

            final SessionRegistry.EnqueuedMessage msg = sessionQueue.poll();
            if (null == msg) {
                return;
            }
            inflictSlots.decrementAndGet();
            int sendPacketId = mqttConnection.nextPacketId();
            SessionRegistry.EnqueuedMessage old = inflictWindow.put(sendPacketId, msg);
            if (old != null) {
                old.release();
                inflictSlots.incrementAndGet();
            }
            inflictTimeouts.add(new InFlightPacket(sendPacketId, FLIGHT_BEFORE_RESEND_MS));
            if (msg instanceof SessionRegistry.PubRelMarker) {
                MqttMessage pubRel = MqttConnection.pubRel(sendPacketId);
                mqttConnection.sendIfWritableElseDrop(pubRel);
            } else {
                final SessionRegistry.PublishedMessage msgPub = (SessionRegistry.PublishedMessage) msg;
                msgPub.payload.retain();
                MqttPublishMessage publishMsg = MqttConnection.notRetainedPublishWithMessageId(msgPub.topic.toString(),
                        msgPub.publishingQos,
                        msgPub.payload.retain(), sendPacketId);
                mqttConnection.sendPublish(publishMsg);
            }
        }
    }

    public void writabilityChanged() {
        drainQueueToConnection();
    }

    public void sendQueuedMessagesWhileOffline() {
        LOGGER.trace("Republishing all saved messages for session {} on CId={}", this, this.clientId);
        drainQueueToConnection();
    }

    void sendRetainedPublishOnSessionAtQos(Topic topic, MqttQoS qos, ByteBuf payload) {
        if (qos != MqttQoS.AT_MOST_ONCE) {
            // QoS 1 or 2
            mqttConnection.sendPublishRetainedWithPacketId(topic, qos, payload);
        } else {
            mqttConnection.sendPublishRetainedQos0(topic, qos, payload);
        }
    }

    public void receivedPublishQos2(int messageID, MqttPublishMessage msg) {
        qos2Receiving.put(messageID, msg);
        msg.retain(); // retain to put in the inflight maptree
        MqttPublishMessage old = qos2Receiving.put(messageID, msg);
        // In case of evil client with duplicate msgid.
        ReferenceCountUtil.release(old);

        mqttConnection.sendPublishReceived(messageID);
    }

    public void receivedPubRelQos2(int messageID) {
        final MqttPublishMessage removedMsg = qos2Receiving.remove(messageID);
        ReferenceCountUtil.release(removedMsg);
    }

    Optional<InetSocketAddress> remoteAddress() {
        if (connected()) {
            return Optional.of(mqttConnection.remoteAddress());
        }
        return Optional.empty();
    }


    @Override
    public String toString() {
        return "Session {" +
                "clientId='" + clientId + '\'' +
                ", clean=" + clean +
                ", status=" + status +
                ", inflightSlots=" + inflictSlots +
                '}';
    }
}
