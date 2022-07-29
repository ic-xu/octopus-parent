//package io.octopus.broker.session;
//
//import io.handler.codec.mqtt.*;
//import io.netty.buffer.ByteBufHolder;
//import io.netty.channel.Channel;
//import io.netty.util.ReferenceCountUtil;
//import io.octopus.base.interfaces.ISession;
//import io.octopus.base.message.Message;
//import io.octopus.base.queue.MsgQueue;
//import io.octopus.base.queue.SearchData;
//import io.octopus.base.queue.StoreMsg;
//import io.octopus.base.utils.ObjectUtils;
//import io.octopus.kernel.message.InFlightPacket;
//import io.octopus.base.queue.MsgIndex;
//import io.octopus.base.subscriptions.Subscription;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//
//import java.io.IOException;
//import java.net.InetSocketAddress;
//import java.util.*;
//import java.util.concurrent.ConcurrentHashMap;
//import java.util.concurrent.DelayQueue;
//import java.util.concurrent.atomic.AtomicInteger;
//import java.util.concurrent.atomic.AtomicReference;
//
//import static io.handler.codec.mqtt.MqttQoS.*;
//
//public class Session implements ISession {
//
//    private static final Logger LOGGER = LoggerFactory.getLogger(Session.class);
//    private static final int FLIGHT_BEFORE_RESEND_MS = 5_000;
//    private static int inflictWindowSize = 10;
//    private final String clientId;
//    private boolean clean;
//    private MqttWillMessage will;
//    private final Queue<MsgIndex> messageIndexQueue;
//    private final AtomicReference<SessionStatus> status = new AtomicReference<>(SessionStatus.DISCONNECTED);
//    private MqttConnection mqttConnection;
//    private int mqttConnectionVersion;
//    //subscriptions save session`s subscriptions，
//    private final Set<Subscription> subscriptions = new HashSet<>();
//    private final Map<Integer, IMessage> inflictWindow = new ConcurrentHashMap<>();
//    private final DelayQueue<InFlightPacket> inflictTimeouts = new DelayQueue<>();
//    private final Map<Integer, MqttPublishMessage> qos2Receiving = new ConcurrentHashMap<>();
//    private final AtomicInteger inflictSlots = new AtomicInteger(inflictWindowSize); // this should be configurable
//    private InetSocketAddress udpInetSocketAddress;
//    private MsgQueue<IMessage> msgQueue;
//
//    public void addInflictWindow(Map<Integer, IMessage> inflictWindows) {
//        for (Integer packetId : inflictWindows.keySet()) {
//            inflictTimeouts.add(new InFlightPacket(packetId, FLIGHT_BEFORE_RESEND_MS));
//            inflictSlots.decrementAndGet();
//        }
//        inflictWindow.putAll(inflictWindows);
//    }
//
//
//    public Map<Integer, IMessage> getInflictWindow() {
//        return inflictWindow;
//    }
//
//
//    public Session(String clientId, boolean clean, MqttWillMessage will, Queue<MsgIndex> sessionQueue,
//                   Integer receiveMaximum, int clientVersion) {
//        this(clientId, clean, sessionQueue, receiveMaximum, clientVersion);
//        this.will = will;
//        this.mqttConnectionVersion = clientVersion;
//    }
//
//    public Session(String clientId, boolean clean, MqttWillMessage will, Queue<MsgIndex> sessionQueue,
//                   Integer receiveMaximum, int clientVersion, MsgQueue<IMessage> msgQueue) {
//        this(clientId, clean, will, sessionQueue, receiveMaximum, clientVersion);
//        this.msgQueue = msgQueue;
//    }
//
//
//    public Session(String clientId, boolean clean, Queue<MsgIndex> messageIndexQueue,
//                   Integer receiveMaximum, int clientVersion) {
//        this.clientId = clientId;
//        this.clean = clean;
//        this.inflictWindowSize = receiveMaximum;
//        this.messageIndexQueue = messageIndexQueue;
//        this.mqttConnectionVersion = clientVersion;
//    }
//
//
//    public Session(String clientId, boolean clean, Queue<MsgIndex> messageIndexQueue, Integer receiveMaximum,
//                   int clientVersion, MsgQueue<IMessage> msgQueue) {
//        this(clientId, clean, messageIndexQueue, receiveMaximum, clientVersion);
//        this.msgQueue = msgQueue;
//
//    }
//
//    @Override
//    public void addSubscriptions(List<Subscription> newSubscriptions) {
//        subscriptions.addAll(newSubscriptions);
//    }
//
//
//    @Override
//    public void cleanSubscribe() {
//
//    }
//
//    @Override
//    public void fireWill() {
//
//    }
//
//
//    @Override
//    public void unSubscriptions(List<String> topic) {
//
//        ArrayList<Subscription> remove = new ArrayList<>();
//        for (Subscription subscription : subscriptions) {
//            if (topic.contains(subscription.getTopicFilter().getValue())) {
//                remove.add(subscription);
//            }
//        }
//        for (Subscription subscription : remove) {
//            subscriptions.remove(subscription);
//        }
//    }
//
//    public boolean hasWill() {
//        return will != null;
//    }
//
//    public MqttWillMessage getWill() {
//        return will;
//    }
//
//    public boolean assignState(SessionStatus expected, SessionStatus newState) {
//        return status.compareAndSet(expected, newState);
//    }
//
//    public void closeImmediately() {
//        mqttConnection.dropConnection();
//    }
//
//    public void disconnect() {
//        final boolean res = assignState(SessionStatus.CONNECTED, SessionStatus.DISCONNECTING);
//        if (!res) {
//            LOGGER.info("this status is SessionStatus.DISCONNECTING");
//            return;
//        }
//
//        mqttConnection = null;
//        will = null;
//        assignState(SessionStatus.DISCONNECTING, SessionStatus.DISCONNECTED);
//    }
//
//    public boolean isClean() {
//        return clean;
//    }
//
//    /**
//     * @param msg msg
//     */
//    public void processPubRec(MqttMessage msg) {
//        int packetId = ((MqttMessageIdVariableHeader) msg.variableHeader()).messageId();
//
//        IMessage removeMsg = inflictWindow.remove(packetId);
//        inflictTimeouts.remove(new InFlightPacket(packetId, FLIGHT_BEFORE_RESEND_MS));
//        if (removeMsg == null) {
//            LOGGER.warn("Received a pubRec with not matching packetId");
//            drainQueueToConnection();
//            return;
//        }
//        ReferenceCountUtil.safeRelease(removeMsg);
//        inflictWindow.put(packetId, msg);
//        inflictTimeouts.add(new InFlightPacket(packetId, FLIGHT_BEFORE_RESEND_MS));
//        MqttMessage pubRel = MqttConnection.pubRel(packetId);
//        mqttConnection.sendIfWritableElseDrop(pubRel);
////        drainQueueToConnection();
//    }
//
//    public void processPubComp(int messageId) {
//        IMessage removeMsg = inflictWindow.remove(messageId);
//        inflictTimeouts.remove(new InFlightPacket(messageId, FLIGHT_BEFORE_RESEND_MS));
//        if (removeMsg == null) {
//            LOGGER.warn("Received a PUBCOMP with not matching packetId");
//            return;
//        }
//        try {
//            ReferenceCountUtil.release(removeMsg);
//        } catch (Exception ignored) {
//        }
//
//        inflictSlots.incrementAndGet();
//        drainQueueToConnection();
//    }
//
//    /**
//     * @param storeMsg
//     * @param directPublish Send directly
//     */
//    public void sendPublishOnSessionAtQos(StoreMsg<IMessage> storeMsg, boolean directPublish) {
//        if (storeMsg.getMsg() instanceof MqttPublishMessage) {
//            MqttPublishMessage msg = (MqttPublishMessage) storeMsg.getMsg();
//            msg.variableHeader().setPacketId(mqttConnection.nextPacketId());
//            switch (msg.fixedHeader().qosLevel()) {
//                case AT_MOST_ONCE:
//                    sendPublishNotRetainedQos0(storeMsg, directPublish);
//                    break;
//                //这里发布的时候两个使用同样的处理方法即可
//                case AT_LEAST_ONCE:
//                    sendPublishQos1(storeMsg, directPublish);
//                    break;
//                case EXACTLY_ONCE:
//                    sendPublishQos2(storeMsg, directPublish);
////                sendPublish(topic, qos, payload);
//                    break;
//
//                default:
//                    LOGGER.error("Not admissible");
//                    break;
//            }
//        }
//
//    }
//
//
//    void sendPublishNotRetainedQos0(StoreMsg<IMessage> storeMsg, boolean directPublish) {
//        mqttConnection.sendPublish((MqttPublishMessage) storeMsg.getMsg());
//    }
//
//    private void sendPublishQos1(StoreMsg<IMessage> storeMsg, boolean directPublish) {
//        if (!connected() && isClean()) {
//            //pushing messages to disconnected not clean session
//            return;
//        }
//        if (!(storeMsg.getMsg() instanceof MqttPublishMessage)) {
//            LOGGER.trace("storeMsg type error ");
//        }
//        MqttPublishMessage publishMsg = (MqttPublishMessage) storeMsg.getMsg();
//        if (canSkipQueue() || directPublish) {
//            publishMsg.variableHeader().setPacketId(mqttConnection.nextPacketId());
//            inflictSlots.decrementAndGet();
//            IMessage old = inflictWindow.put(publishMsg.variableHeader().packetId(), publishMsg.copy());
//            // If there already was something, release it.
//            if (old instanceof ByteBufHolder) {
//                try {
//                    ReferenceCountUtil.safeRelease(old);
//                } catch (Exception ignored) {
//                }
//                inflictSlots.incrementAndGet();
//            }
//            inflictTimeouts.add(new InFlightPacket(publishMsg.variableHeader().packetId(), FLIGHT_BEFORE_RESEND_MS));
//            mqttConnection.sendPublish(publishMsg.copy());
//            LOGGER.debug("Write direct to the peer, inflict slots: {}", inflictSlots.get());
//            if (inflictSlots.get() == 0) {
//                mqttConnection.flush();
//            }
//            // TODO drainQueueToConnection();?
////            drainQueueToConnection();
//        } else {
//            if (!ObjectUtils.isEmpty(storeMsg.getIndex())) {
//                messageIndexQueue.offer(storeMsg.getIndex());
//            }
//            try {
//                publishMsg.release();
//            } catch (Exception e) {
//                LOGGER.error("error {}", e);
//            }
//
//            drainQueueToConnection();
//        }
//    }
//
//    private void sendPublishQos2(StoreMsg<IMessage> storeMsg, boolean directPublish) {
//        if (!(storeMsg.getMsg() instanceof MqttPublishMessage)) {
//            LOGGER.trace("storeMsg type error ");
//        }
//        MqttPublishMessage publishMsg = (MqttPublishMessage) storeMsg.getMsg();
//        if (canSkipQueue() || directPublish) {
//            publishMsg.variableHeader().setPacketId(mqttConnection.nextPacketId());
//            inflictSlots.decrementAndGet();
//            inflictWindow.put(publishMsg.variableHeader().packetId(), publishMsg.copy());
//            inflictTimeouts.add(new InFlightPacket(publishMsg.variableHeader().packetId(), FLIGHT_BEFORE_RESEND_MS));
//
//            mqttConnection.sendPublish(publishMsg.copy());
////            //TODO  drainQueueToConnection(); ?
//        } else {
//
//            if (!ObjectUtils.isEmpty(storeMsg.getIndex())) {
//                messageIndexQueue.offer(storeMsg.getIndex());
//            }
//            publishMsg.release();
//        }
//        drainQueueToConnection();
//    }
//
//    //if version is 2 ,can send customer message
//    public void sendCustomerMessage(MqttMessage mqttMessage) {
//        if (mqttConnectionVersion == MqttVersion.MQTT_2.protocolLevel())
//            mqttConnection.sendIfWritableElseDrop(mqttMessage);
//    }
//
//
//    private boolean canSkipQueue() {
//        return messageIndexQueue.isEmpty() &&
//                inflictSlots.get() > 0 &&
//                connected() &&
//                mqttConnection.channel.isWritable();
//    }
//
//
//    private boolean inflictHasSlotsAndConnectionIsUp() {
//        return inflictSlots.get() > 0 &&
//                connected() &&
//                mqttConnection.channel.isWritable();
//    }
//
//    public void pubAckReceived(int ackPacketId) {
//        // TODO remain to invoke in somehow m_interceptor.notifyMessageAcknowledged
//        LOGGER.trace("received a pubAck packetId is {} ", ackPacketId);
//        IMessage removeMsg = inflictWindow.remove(ackPacketId);
//        inflictTimeouts.remove(new InFlightPacket(ackPacketId, FLIGHT_BEFORE_RESEND_MS));
//        if (removeMsg == null) {
//            LOGGER.trace("Received a pubAck with not matching packetId  {} ", ackPacketId);
//        } else if (removeMsg instanceof ByteBufHolder) {
//            ((ByteBufHolder) removeMsg).release();
//            inflictSlots.incrementAndGet();
//        }
//        drainQueueToConnection();
//    }
//
//    public void flushAllQueuedMessages() {
//        drainQueueToConnection();
//    }
//
//    public void reSendInflictNotAcked() {
//        if (inflictTimeouts.size() == 0) {
//            inflictWindow.clear();
//        } else {
//            Collection<InFlightPacket> expired = new ArrayList<>(inflictWindowSize);
//            inflictTimeouts.drainTo(expired);
//            debugLogPacketIds(expired);
//            for (InFlightPacket notAckPacketId : expired) {
//                if (inflictWindow.containsKey(notAckPacketId.getPacketId())) {
//                    IMessage message = inflictWindow.remove(notAckPacketId.getPacketId());
//                    if (null == message) {
//                        // Already acked...
//                        LOGGER.warn("Already acked...");
//                    } else if (message instanceof MqttPubRelMessage) {
//                        mqttConnection.sendIfWritableElseDrop((MqttPubRelMessage) message);
//                    } else if (message instanceof MqttPublishMessage) {
//                        final MqttPublishMessage publishMsg = (MqttPublishMessage) message;
////                        MqttPublishMessage publishMsg = publishNotRetainedDuplicated(notAckPacketId, msg.getTopic(), msg.getPublishingQos(), msg.getPayload());
//                        inflictTimeouts.add(new InFlightPacket(notAckPacketId.getPacketId(), FLIGHT_BEFORE_RESEND_MS));
//                        mqttConnection.sendPublish(publishMsg);
//                    }
//                    ReferenceCountUtil.safeRelease(message);
//                }
//            }
//        }
//    }
//
//
//    private void debugLogPacketIds(Collection<InFlightPacket> expired) {
//        if (!LOGGER.isDebugEnabled() || expired.isEmpty()) {
//            return;
//        }
//
//        StringBuilder sb = new StringBuilder();
//        for (InFlightPacket packet : expired) {
//            sb.append(packet.getPacketId()).append(", ");
//        }
//        LOGGER.debug("Resending {} in flight packets [{}]", expired.size(), sb);
//    }
//
//    // consume the queue
//    private void drainQueueToConnection() {
//        reSendInflictNotAcked();
//        while (!messageIndexQueue.isEmpty() && inflictHasSlotsAndConnectionIsUp()) {
//
//            //todo there will block
//            final MsgIndex msgIndex = messageIndexQueue.poll();
//            if (null == msgIndex) {
//                continue;
//            }
//
//            StoreMsg<IMessage> msg = msgQueue.poll(new SearchData(clientId, msgIndex));
//            if (null == msg) {
//                return;
//            }
//            if (msg.getMsg() instanceof MqttMessage) {
//                final MqttMessage mqttMessage = (MqttMessage) msg.getMsg();
//                switch (mqttMessage.fixedHeader().messageType()) {
//                    case CUSTOMER:
//                        break;
//                    case PUBLISH:
//                        MqttPublishMessage msgPub = (MqttPublishMessage) mqttMessage;
//                        msgPub.variableHeader().setPacketId(mqttConnection.nextPacketId());
//                        if (msgPub.fixedHeader().qosLevel() != AT_MOST_ONCE) {
//                            inflictSlots.decrementAndGet();
//                            IMessage old = inflictWindow.put(msgPub.variableHeader().packetId(), msgPub.copy());
//                            ReferenceCountUtil.safeRelease(old);
//                            inflictSlots.incrementAndGet();
//
//                            inflictTimeouts.add(new InFlightPacket(msgPub.variableHeader().packetId(), FLIGHT_BEFORE_RESEND_MS));
//                        }
//                        mqttConnection.sendPublish(msgPub);
//                        break;
//                    case PUBACK:
//                    case PUBREC:
//                    case PUBREL:
//                    case PUBCOMP:
//                        MqttMessageIdVariableHeader variableHeader = (MqttMessageIdVariableHeader) mqttMessage.variableHeader();
//                        inflictSlots.decrementAndGet();
//                        final int packetId = variableHeader.messageId();
//                        IMessage old = inflictWindow.put(packetId, mqttMessage);
//                        ReferenceCountUtil.safeRelease(old);
//                        inflictSlots.incrementAndGet();
//
//                        inflictTimeouts.add(new InFlightPacket(packetId, FLIGHT_BEFORE_RESEND_MS));
//                        MqttMessage pubRel = MqttConnection.pubRel(packetId);
//                        mqttConnection.sendIfWritableElseDrop(pubRel);
//                        break;
//                    default:
//                        break;
//                }
//                ReferenceCountUtil.safeRelease(mqttMessage);
//            } else {
//                LOGGER.trace("error msg {}", msg);
//            }
//
//        }
//    }
//
//
//    public void writeAbilityChanged() {
//        drainQueueToConnection();
//    }
//
//    public void sendQueuedMessagesWhileOffline() {
//        LOGGER.trace("Republishing all saved messages for session {} on CId={}", this, this.clientId);
//        drainQueueToConnection();
//    }
//
//
//    public void sendPublishReceivedQos2(MqttPublishMessage msg) {
//        final int messageId = msg.variableHeader().packetId();
////        msg.retain(); // retain to put in the inflight maptree
//        MqttPublishMessage old = qos2Receiving.put(messageId, msg.copy());
//        // In case of evil client with duplicate msgid.
//        if (null != old) {
//            ReferenceCountUtil.safeRelease(old);
//        }
//        mqttConnection.sendPublishReceived(messageId);
//    }
//
//    public void receivedPubRelQos2(int messageId) throws IOException {
//        final MqttPublishMessage removedMsg = qos2Receiving.remove(messageId);
//        if (null != removedMsg) {
//            mqttConnection.receiverQos2(removedMsg, clientId, mqttConnection.getUsername(), messageId, mqttConnection);
////            ReferenceCountUtil.safeRelease(removedMsg);
//        }
//    }
//
//    Optional<InetSocketAddress> remoteAddress() {
//        if (connected()) {
//            return Optional.of(mqttConnection.remoteAddress());
//        }
//        return Optional.empty();
//    }
//
//
//    public void cleanSessionQueue() {
//        while (messageIndexQueue.size() > 0) {
//            messageIndexQueue.poll();
//        }
//    }
//
//
//    public void update(boolean clean, MqttWillMessage will) {
//        this.clean = clean;
//        this.will = will;
//    }
//
//    public void markConnecting() {
//        assignState(SessionStatus.DISCONNECTED, SessionStatus.CONNECTING);
//    }
//
//    public boolean completeConnection() {
//        return assignState(SessionStatus.CONNECTING, SessionStatus.CONNECTED);
//    }
//
//    public void bind(MqttConnection mqttConnection) {
//        this.mqttConnection = mqttConnection;
//    }
//
//    @Override
//    public boolean receiveMessage(Message msg) {
//        //TODO 收到消息
//
//        return false;
//    }
//
//    @Override
//    public boolean sendMessage(Message msg) {
//
//        //TODO 发送消息
//        return false;
//    }
//
//    @Override
//    public void bindUdpInetSocketAddress(InetSocketAddress inetSocketAddress) {
//        this.udpInetSocketAddress = inetSocketAddress;
//    }
//
//    @Override
//    public InetSocketAddress getUdpInetSocketAddress() {
//        return this.udpInetSocketAddress;
//    }
//
//    @Override
//    public List<Subscription> subscriptions(List<Subscription> subscriptions) {
//
//        return new ArrayList<>();
//    }
//
////    @Override
////    public void unSubscriptions(List<Subscription> subscriptions) {
////
////    }
//
//    @Override
//    public void handleConnectionLost() {
//
//    }
//
//    @Override
//    public String getUsername() {
//        //TODO username
//        return "";
//    }
//
//    public boolean disconnected() {
//        return status.get() == SessionStatus.DISCONNECTED;
//    }
//
//    public boolean connected() {
//        return status.get() == SessionStatus.CONNECTED;
//    }
//
//    @Override
//    public String getClientId() {
//        return clientId;
//    }
//
//    public List<Subscription> getSubscriptions() {
//        return new ArrayList<>(subscriptions);
//    }
//
//
//    @Override
//    public String toString() {
//        return "Session {" +
//                "clientId='" + clientId + '\'' +
//                ", clean=" + clean +
//                ", status=" + status +
//                ", inflightSlots=" + inflictSlots +
//                '}';
//    }
//}
