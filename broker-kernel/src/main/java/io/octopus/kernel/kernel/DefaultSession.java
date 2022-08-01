package io.octopus.kernel.kernel;

import io.netty.util.ReferenceCountUtil;
import io.octopus.kernel.kernel.connect.AbstractConnection;
import io.octopus.kernel.kernel.message.*;
import io.octopus.kernel.kernel.queue.MsgIndex;
import io.octopus.kernel.kernel.queue.MsgQueue;
import io.octopus.kernel.kernel.queue.SearchData;
import io.octopus.kernel.kernel.queue.StoreMsg;
import io.octopus.kernel.kernel.subscriptions.Subscription;
import io.octopus.kernel.utils.ObjectUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * @author chenxu
 * @version 1
 * @date 2022/6/21 18:51
 */
public class DefaultSession implements ISession {

    private final Logger logger = LoggerFactory.getLogger(DefaultSession.class);
    /**
     * 消息分发器
     */
    protected final IPostOffice postOffice;

    /**
     * session 对应的clientIds
     */
    protected final String clientId;

    /**
     * session 对应username
     */
    protected final String userName;

    /**
     * session 对应的遗嘱消息
     */
    private KernelPayloadMessage willMsg;

    /**
     * 索引队列,专门存储 qos1 的消息索引
     */
    protected final Queue<MsgIndex> qos1Queue;

    /**
     * 索引队列,专门存储 qos2的消息索引
     */
    protected final Queue<MsgIndex> qos2Queue;

    /**
     * 发送中窗口大小
     */
    private final Integer inflictWindowSize;

    /**
     * 客户端版本信息
     */
    protected final Integer clientVersion;

    /**
     * 消息队列大小
     */
    protected final MsgQueue<KernelPayloadMessage> msgQueue;

    /**
     * 清除队列服务
     */
    protected final ExecutorService drainQueueService;

    /**
     * 发送中的窗口
     */
    protected final Map<Short, KernelPayloadMessage> qos1InflictWindow = new ConcurrentHashMap<>();

    /**
     * 消息发送时间，超过这个时间之后就判定为超
     */
    protected static final Long FLIGHT_BEFORE_RESEND_MS = 5_000L;

    /**
     * 超时队列
     */
    protected final DelayQueue<InFlightPacket> inflictTimeouts = new DelayQueue<>();

    /**
     * 同时可以发送多少个消息
     */
    protected final AtomicInteger inflictSlots; // this should be configurable

    /**
     * 发送包的id
     */
    protected final AtomicInteger lastPacketId = new AtomicInteger(2);


    protected AtomicReference<SessionStatus> status = new AtomicReference<>(SessionStatus.DISCONNECTED);

    /**
     * 和session 绑定的connection
     */
    protected AbstractConnection connection;

    private Boolean clean;


    private InetSocketAddress udpInetSocketAddress;


    /**
     * subscriptions save session`s subscriptions，订阅的主题
     */
    protected Set<String> subTopicStr = new HashSet<>();

    /**
     * qos2的消息的包装对象。
     */
    private Qos2SenderWrapper qos2SenderMsg;

    public DefaultSession(IPostOffice postOffice, String userName,
                          String clientId, Boolean clean, KernelPayloadMessage willMsg,
                          Queue<MsgIndex> qos1Queue, Queue<MsgIndex> qos2Queue,
                          Integer inflictWindowSize, Integer clientVersion,
                          MsgQueue<KernelPayloadMessage> msgQueue,
                          ExecutorService drainQueueService) {

        this.postOffice = postOffice;
        this.userName = userName;
        this.clientId = clientId;
        this.clean = clean;
        this.willMsg = willMsg;
        this.qos1Queue = qos1Queue;
        this.qos2Queue = qos2Queue;
        this.inflictWindowSize = inflictWindowSize;
        this.clientVersion = clientVersion;
        this.msgQueue = msgQueue;
        this.drainQueueService = drainQueueService;
        inflictSlots = new AtomicInteger(inflictWindowSize); // this should be configurable
    }


    /**
     * 接收到客户端发送过来的消息
     *
     * @param msg msg；
     */
    @Override
    public boolean receiveMsg(KernelPayloadMessage msg) {
        return postOffice.processReceiverMsg(msg, this);
    }


    /**
     * 发送消息，postOffice 调用这个消息发送相关消息
     *
     * @param storeMsg      msg
     * @param directPublish Send directly
     */
    @Override
    public void sendMsgAtQos(StoreMsg<KernelPayloadMessage> storeMsg, Boolean directPublish) {
        switch (storeMsg.getMsg().getQos()) {
            case AT_MOST_ONCE:
                sendMsgAtQos0(storeMsg, directPublish);
                break;
            //这里发布的时候两个使用同样的处理方法即可
            case AT_LEAST_ONCE:
                sendMsgAtQos1(storeMsg, directPublish);
                break;
            case EXACTLY_ONCE:
                sendMsgAtQos2(storeMsg, directPublish);
                break;
            case UDP:
                // TODO 还没有实现UDP 推送消息
                logger.error("Not admissible {}", "UDP");
                break;
            default:
                logger.error("Not admissible");
        }
    }


    private void sendMsgAtQos0(StoreMsg<KernelPayloadMessage> storeMsg, Boolean directPublish) {
        connection.sendIfWritableElseDrop(storeMsg.getMsg());
    }


    private void sendMsgAtQos1(StoreMsg<KernelPayloadMessage> storeMsg, Boolean directPublish) {
        if (!connected() && isClean()) { //pushing messages to disconnected not clean session
            return;
        }
        KernelPayloadMessage msg = storeMsg.getMsg();

        if (canSkipQos1Queue() || directPublish) {
            inflictSlots.decrementAndGet();
            KernelPayloadMessage old = qos1InflictWindow.put(msg.packageId(), msg.retain());
            // If there already was something, release it.
            if (old != null) {
                try {
                    ReferenceCountUtil.safeRelease(old);
                } catch (Exception ignored) {
                }
                inflictSlots.incrementAndGet();
            }

            inflictTimeouts.add(new InFlightPacket(msg.packageId(), DefaultSession.FLIGHT_BEFORE_RESEND_MS));
            connection.sendIfWritableElseDrop(msg);
            logger.debug("Write direct to the peer, inflict slots: {}", inflictSlots.get());
            if (inflictSlots.get() == 0) {
                connection.flush();
            }
            // TODO drainQueueToConnection();?
            //            drainQueueToConnection();
        } else {
            qos1Queue.offer(storeMsg.getIndex());
//            offerMsgIndex(storeMsg.getIndex(), msg);

//            drainQueueToConnection();
        }
    }

    @Override
    public Boolean receivePubAcK(Short ackPacketId) {
        // TODO remain to invoke in somehow m_interceptor.notifyMessageAcknowledged
        logger.trace("received a pubAck packetId is {} ", ackPacketId);
        KernelPayloadMessage removeMsg = qos1InflictWindow.remove(ackPacketId);
        inflictTimeouts.remove(new InFlightPacket(ackPacketId, DefaultSession.FLIGHT_BEFORE_RESEND_MS));
        if (removeMsg == null) {
            logger.trace("Received a pubAck with not matching packetId  {} ", ackPacketId);
        } else {
            ReferenceCountUtil.safeRelease(removeMsg);
            inflictSlots.incrementAndGet();
        }
        drainQos1QueueToConnection();
        return true;
    }


    private void sendMsgAtQos2(StoreMsg<KernelPayloadMessage> storeMsg, Boolean directPublish) {
        if (!connected() && isClean()) {
            //pushing messages to disconnected not clean session
            return;
        }
        KernelPayloadMessage msg = storeMsg.getMsg();

        if (canSkipQos2Queue() || directPublish) {
            ///缓存qos2消息
            qos2SenderMsg = new Qos2SenderWrapper(msg);
            ///添加超时检测
            inflictTimeouts.add(new InFlightPacket(msg.packageId(), DefaultSession.FLIGHT_BEFORE_RESEND_MS));
            ///发送消息
            connection.sendIfWritableElseDrop(msg);
            logger.debug("Write direct to the peer, inflict slots: {}", inflictSlots.get());
            if (inflictSlots.get() == 0) {
                connection.flush();
            }
        } else {
            qos2Queue.offer(storeMsg.getIndex());
//            drainQueueToConnection();
        }
    }

    @Override
    public void receivePubRec(Short recPacketId) {
        logger.trace("received a pubAck packetId is {} ", recPacketId);
        if (null != qos2SenderMsg && Objects.equals(qos2SenderMsg.getQos2Msg().packageId(), recPacketId)) {
            qos2SenderMsg.setReceiverPubRec(true);

        }
        logger.trace("Received a pubAck with not matching packetId  {} ", recPacketId);
    }

    @Override
    public Boolean receivePubReL(Short relPacketId) {

        return false;
    }

    @Override
    public Boolean receivePubComp(Short pubCompPacketId) {
        if (null != qos2SenderMsg && Objects.equals(qos2SenderMsg.getQos2Msg().packageId(), pubCompPacketId)) {
            qos2SenderMsg = null;
            return true;
        }
        return false;
    }

    /**
     * 判断是否可以跳过qos2的消息队列
     *
     * @return boolean
     */
    private Boolean canSkipQos2Queue() {
        return qos2Queue.isEmpty() && connected() && connection.getChannel().isWritable() && qos2SenderMsg == null;
    }

    /**
     * 判断是否可以跳过qos1的消息队列
     *
     * @return boolean
     */
    private Boolean canSkipQos1Queue() {
        return qos1Queue.isEmpty() && inflictSlots.get() > 0 && connected() && connection.getChannel().isWritable();
    }


    public Map<Short, KernelPayloadMessage> getQos1InflictWindow() {
        return qos1InflictWindow;
    }


    public void bind(AbstractConnection connection) {
        this.connection = connection;
    }


    @Override
    public List<Subscription> subscriptions(List<Subscription> subscriptions) {
        List<Subscription> subscriptionsResult = postOffice.subscriptions(this, subscriptions);
        List<String> subscriptionStr = subscriptionsResult.stream()
                .map(subscription -> subscription.topicFilter.getValue()).toList();
        this.subTopicStr.addAll(subscriptionStr);
        return subscriptionsResult;
    }


    public void addQos1InflictWindow(Map<Short, KernelPayloadMessage> inflictWindows) {
        inflictWindows.forEach((packetId, msg) -> {
            inflictTimeouts.add(new InFlightPacket(packetId, FLIGHT_BEFORE_RESEND_MS));
            inflictSlots.decrementAndGet();
        });
    }


    public void writeAbilityChanged() {
        flushAllQueuedMessages();
    }

    /**
     * 推送队列中的消息
     */
    public void flushAllQueuedMessages() {
        // 清理Qos1 的消息
        drainQos1QueueToConnection();

        // 清理QOS2的消息
        drainQos2QueueToConnection();
    }


    /**
     * doDrainQueueToConnection
     */
    protected void doDrainQos1QueueToConnection() {
        reSendInflictNotAcked();
        while (!qos1Queue.isEmpty() && inflictHasSlotsAndConnectionIsUp()) {
            MsgIndex msgIndex = qos1Queue.poll();
            if (!ObjectUtils.isEmpty(msgIndex)) {

                //notify: there must not use foreach(), there queue not implement
                //  @Override
                //    public Iterator<MsgIndex> iterator() {
                //        return null;
                //    }
                //and inflictHasSlotsAndConnectionIsUp is true
                StoreMsg<KernelPayloadMessage> msg = msgQueue.poll(new SearchData(clientId, msgIndex));
                //        if (!ObjectUtils.isEmpty(msg)) {
                //          msg.getMsg match {
                //            case mqttMessage: MqttMessage =>
                //              mqttMessage.fixedHeader.messageType match {
                //                case MqttMessageType.CUSTOMER =>
                //                case MqttMessageType.PUBLISH =>
                //                  val msgPub = mqttMessage.asInstanceOf[MqttPublishMessage]
                //                  msgPub.variableHeader.setPacketId(nextPacketId)
                //                  if (msgPub.fixedHeader.qosLevel ne MqttQoS.AT_MOST_ONCE) {
                //                    inflictSlots.decrementAndGet
                //                    val old = inflictWindow.put(msgPub.variableHeader.packetId, msgPub.copy)
                //                    ReferenceCountUtil.safeRelease(old)
                //                    inflictSlots.incrementAndGet
                //                    inflictTimeouts.add(new InFlightPacket(msgPub.variableHeader.packetId, FLIGHT_BEFORE_RESEND_MS))
                //                  }
                //                  connection.sendPublish(msgPub)
                //
                //                case MqttMessageType.PUBACK =>
                //                case MqttMessageType.PUBREC =>
                //                case MqttMessageType.PUBREL =>
                //                case MqttMessageType.PUBCOMP =>
                //                  val variableHeader = mqttMessage.variableHeader.asInstanceOf[MqttMessageIdVariableHeader]
                //                  inflictSlots.decrementAndGet
                //                  val packetId = variableHeader.messageId
                //                  val old = inflictWindow.put(packetId, mqttMessage)
                //                  ReferenceCountUtil.safeRelease(old)
                //                  inflictSlots.incrementAndGet
                //                  inflictTimeouts.add(new InFlightPacket(packetId, FLIGHT_BEFORE_RESEND_MS))
                //                  val pubRel = connection.pubRel(packetId)
                //                  connection.sendIfWritableElseDrop(pubRel)
                //
                //                case _ =>
                //
                //              }
                //
                //            case _ => logger.trace("error msg {}", msg)
                //          }
                //          ReferenceCountUtil.safeRelease(msg.getMsg)
                //        }
            }
        }
    }

    private Boolean inflictHasSlotsAndConnectionIsUp() {
        return inflictSlots.get() > 0 && connected() && connection.getChannel().isWritable();
    }


    public void reSendInflictNotAcked() {
        if (this.inflictTimeouts.size() == 0) {
            qos1InflictWindow.clear();
        } else {
            List<InFlightPacket> expired = new ArrayList<>(inflictWindowSize);
            this.inflictTimeouts.drainTo(expired);
            debugLogPacketIds(expired);
            expired.forEach(notAckPacketId -> {
                if (qos1InflictWindow.containsKey(notAckPacketId.getPacketId())) {
                    KernelPayloadMessage message = qos1InflictWindow.remove(notAckPacketId.getPacketId());
                    if (null == message) { // Already acked...
                        logger.warn("Already acked...");
                    } else {

                      /*  message match {
                            //            case pubRelMsg: MqttPubRelMessage => connection.sendIfWritableElseDrop(pubRelMsg)
                            //            case publishMsg: MqttPublishMessage =>
                            //              //                        MqttPublishMessage publishMsg = publishNotRetainedDuplicated(notAckPacketId, msg.getTopic(), msg.getPublishingQos(), msg.getPayload());
                            //              inflictTimeouts.add(new InFlightPacket(notAckPacketId.getPacketId, FLIGHT_BEFORE_RESEND_MS))
                            //              connection.sendPublish(publishMsg)
                            case _ =>logger.warn("Already acked...")
                        }*/

                    }
                    ReferenceCountUtil.safeRelease(message);
                }
            });
        }
    }


    private void debugLogPacketIds(Collection<InFlightPacket> expired) {
        if (!logger.isDebugEnabled() || expired.isEmpty()) {
            return;
        }
        StringBuilder sb = new StringBuilder();
        expired.forEach(packet -> sb.append(packet.getPacketId()).append(", "));
        logger.debug("Resending {} in flight packets [{}]", expired.size(), sb);
    }


    @Override
    public void cleanSubscribe() {
        postOffice.cleanSubscribe(this, subTopicStr);
        unSubscriptions(new HashSet<>(subTopicStr));
    }

    /**
     * unSubscriptions
     *
     * @param topics topic
     */
    @Override
    public void unSubscriptions(Set<String> topics) {
        // unSubscriptions
        postOffice.unSubscriptions(this, topics);
        // remove session`s topic
        subTopicStr.removeAll(topics);
    }


    public void update(Boolean clean, KernelPayloadMessage will) {
        this.clean = clean;
        this.willMsg = will;
    }


    // consume the queue
    protected void drainQos1QueueToConnection() {
        drainQueueService.submit(new DrainQos1QueueWorker());
    }

    /**
     * 推送qos2消息
     */
    protected void drainQos2QueueToConnection() {
        if (null == qos2SenderMsg) {
            MsgIndex msgIndex = qos2Queue.poll();
            if (!ObjectUtils.isEmpty(msgIndex)) {
                StoreMsg<KernelPayloadMessage> storeMsg = msgQueue.poll(new SearchData(clientId, msgIndex));
                KernelPayloadMessage msg = storeMsg.getMsg();

                /// 一直找到正常的消息为止
                while (null == msg) {
                    storeMsg = msgQueue.poll(new SearchData(clientId, msgIndex));
                    msg = storeMsg.getMsg();
                }

                ///缓存qos2消息
                qos2SenderMsg = new Qos2SenderWrapper(msg);
                ///添加超时检测
                inflictTimeouts.add(new InFlightPacket(msg.packageId(), DefaultSession.FLIGHT_BEFORE_RESEND_MS));
                ///发送消息
                connection.sendIfWritableElseDrop(msg);
                logger.debug("Write direct to the peer, inflict slots: {}", inflictSlots.get());
                if (inflictSlots.get() == 0) {
                    connection.flush();
                }

            }
        }
    }


    class DrainQos1QueueWorker implements Runnable {

        @Override
        public void run() {
            try {
                doDrainQos1QueueToConnection();
            } catch (Exception e) {
                e.printStackTrace();
            }

        }
    }


    @Override
    public String getUsername() {
        return userName;
    }

    public int nextPacketId() {
        if (lastPacketId.incrementAndGet() > 65535) {
            lastPacketId.set(2);
            return 2;
        }
        return lastPacketId.get();
    }


    @Override
    public void handleConnectionLost() {
        //TODO connectionLost
        //    postOffice.dispatchDisconnection(clientID, userName)

        //TODO postOffice.dispatchConnectionLost(clientID, userName)

        //fireWillMsg
        if (null != willMsg) {
            postOffice.fireWill(willMsg, this);
        }
        //    //unSubscription
        //    cleanSubscribe()
        //update status
        disconnect();
    }

    private void disconnect() {
        Boolean res = assignState(SessionStatus.CONNECTED, SessionStatus.DISCONNECTING);
        if (!res) {
            logger.info("this status is SessionStatus.DISCONNECTING");
            return;
        }
        connection = null;
        willMsg = null;
        assignState(SessionStatus.DISCONNECTING, SessionStatus.DISCONNECTED);
    }

    /**
     * uddate the status of session
     *
     * @param expected expected Status
     * @param newState new Status
     * @return
     */
    public Boolean assignState(SessionStatus expected, SessionStatus newState) {
        return status.compareAndSet(expected, newState);
    }


    @Override
    public void bindUdpInetSocketAddress(InetSocketAddress inetSocketAddress) {
        this.udpInetSocketAddress = inetSocketAddress;

    }


    @Override
    public InetSocketAddress getUdpInetSocketAddress() {
        return udpInetSocketAddress;
    }


    public Boolean disconnected() {
        return status.get() == SessionStatus.DISCONNECTED;
    }

    @Override
    public String getClientId() {
        return clientId;
    }

    public Boolean isClean() {
        return clean;
    }

    public Boolean connected() {
        return status.get() == SessionStatus.CONNECTED;
    }

    @Override
    public Set<String> getSubTopicList() {
        return subTopicStr;
    }


    public Optional<InetSocketAddress> remoteAddress() {
        if (connected()) {
            return Optional.of(connection.remoteAddress());
        } else {
            return Optional.empty();
        }
    }


    public void cleanSessionQueue() {
        while (qos1Queue.size() > 0) {
            qos1Queue.poll();
        }

        while (qos2Queue.size() > 0) {
            qos2Queue.poll();
        }
    }


    public void markConnecting() {
        assignState(SessionStatus.DISCONNECTED, SessionStatus.CONNECTING);
    }

    public Boolean completeConnection() {
        return assignState(SessionStatus.CONNECTING, SessionStatus.CONNECTED);
    }


    public void closeImmediately() {
        if (!ObjectUtils.isEmpty(connection)) {
            connection.dropConnection();
        }
    }


    public void sendQueuedMessagesWhileOffline() {
        logger.trace("Republishing all saved messages for session {} on CId={}", this, this.clientId);
        flushAllQueuedMessages();
    }

    @Override
    public String toString() {
        return "Session {" + "clientId='" + clientId + '\'' + ", clean=" + clean + ", status=" + status + ", inflightSlots=" + inflictSlots + '}';
    }
}
