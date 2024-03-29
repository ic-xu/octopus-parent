package io.octopus.kernel.kernel;

import io.netty.util.ReferenceCountUtil;
import io.octopus.kernel.kernel.connect.AbstractConnection;
import io.octopus.kernel.kernel.message.*;
import io.octopus.kernel.kernel.queue.Index;
import io.octopus.kernel.kernel.queue.MsgRepository;
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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author chenxu
 * @version 1
 * @date 2022/6/21 18:51
 */
public class DefaultSession implements ISession, Runnable {

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
    protected final Queue<Index> qos1Queue;

    /**
     * 索引队列,专门存储 qos2的消息索引
     */
    protected final Queue<Index> qos2Queue;

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
    protected final MsgRepository<KernelPayloadMessage> msgRepository;

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
     * qos2的消息的包装对象。目的是拆分qos1 和qos2的逻辑队列，不让qos2 的消息阻塞 qos1 的消息发送
     */
    private Qos2SenderWrapper qos2SenderMsg;

    public DefaultSession(IPostOffice postOffice, String userName,
                          String clientId, Boolean clean, KernelPayloadMessage willMsg,
                          Queue<Index> qos1Queue, Queue<Index> qos2Queue,
                          Integer inflictWindowSize, Integer clientVersion,
                          MsgRepository<KernelPayloadMessage> msgRepository) {

        this.postOffice = postOffice;
        this.userName = userName;
        this.clientId = clientId;
        this.clean = clean;
        this.willMsg = willMsg;
        this.qos1Queue = qos1Queue;
        this.qos2Queue = qos2Queue;
        this.inflictWindowSize = inflictWindowSize;
        this.clientVersion = clientVersion;
        this.msgRepository = msgRepository;
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
        storeMsg.getMsg().setPackageId(nextPacketId());
        switch (storeMsg.getMsg().getQos()) {
            case AT_MOST_ONCE -> sendMsgAtQos0(storeMsg);
            //这里发布的时候两个使用同样的处理方法即可
            case AT_LEAST_ONCE -> sendMsgAtQos1(storeMsg, directPublish);
            case EXACTLY_ONCE -> sendMsgAtQos2(storeMsg, directPublish);

            //TODO UDP 的转发逻辑还没有实现
            case UDP -> logger.error("Not admissible {}", "UDP");
            default -> logger.error("Not admissible");
        }
    }


    private void sendMsgAtQos0(StoreMsg<KernelPayloadMessage> storeMsg) {
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
        } else {
            qos1Queue.offer(storeMsg.getIndex());
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
            //发送收到消息
            KernelMessage kernelMessage = new KernelMessage(recPacketId, PubEnum.PUB_REL);
            connection.sendIfWritableElseDrop(kernelMessage);
        } else {
            logger.trace("Received a pubAck with not matching packetId  {} ", recPacketId);
        }
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
            Index msgIndex = qos1Queue.poll();
            if (!ObjectUtils.isEmpty(msgIndex)) {

                StoreMsg<KernelPayloadMessage> storeMsg = msgRepository.poll(new SearchData(clientId, msgIndex));
                /// 重新开发发送 qos1 的消息。
                sendMsgAtQos1(storeMsg, false);
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
        doDrainQos1QueueToConnection();
    }

    /**
     * 推送qos2消息
     */
    protected void drainQos2QueueToConnection() {
        if (null == qos2SenderMsg) {
            Index msgIndex = qos2Queue.poll();
            if (!ObjectUtils.isEmpty(msgIndex)) {
                StoreMsg<KernelPayloadMessage> storeMsg = msgRepository.poll(new SearchData(clientId, msgIndex));
                KernelPayloadMessage msg = storeMsg.getMsg();

                /// 一直找到正常的消息为止
                while (null == msg) {
                    storeMsg = msgRepository.poll(new SearchData(clientId, msgIndex));
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

    @Override
    public void run() {

    }

    @Override
    public String getUsername() {
        return userName;
    }

    public Short nextPacketId() {
        if (lastPacketId.incrementAndGet() > 65535) {
            lastPacketId.set(2);
            return 2;
        }
        return (short) lastPacketId.get();
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
     * update the status of session
     *
     * @param expected expected Status
     * @param newState new Status
     * @return boolean
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
