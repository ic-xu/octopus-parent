package io.octopus.kernel.kernel;

import io.netty.util.ReferenceCountUtil;
import io.octopus.kernel.kernel.connect.AbstractConnection;
import io.octopus.kernel.kernel.message.*;
import io.octopus.kernel.kernel.subscriptions.Subscription;
import io.octopus.kernel.utils.ObjectUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
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
    protected final Queue<KernelPayloadMessage> sessionQos1MsgQueue;

    /**
     * 索引队列,专门存储 qos2的消息索引
     */
    protected final Queue<KernelPayloadMessage> sessionQos2MsgQueue;

    /**
     * 发送中窗口大小
     */
    private final Integer inflictWindowSize;

    /**
     * 客户端版本信息
     */
    protected final Integer clientVersion;

//    /**
//     * 消息仓库，通过索引Id 去获取具体消息类
//     */
//    protected final IMsgQueue<IMessage> msgRepository;

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

    public DefaultSession(IPostOffice postOffice, String userName, String clientId, Boolean clean,
                          KernelPayloadMessage willMsg, Queue<KernelPayloadMessage> sessionQos1MsgQueue,
                          Queue<KernelPayloadMessage> sessionQos2MsgQueue, Integer inflictWindowSize,
                          Integer clientVersion) {

        this.postOffice = postOffice;
        this.userName = userName;
        this.clientId = clientId;
        this.clean = clean;
        this.willMsg = willMsg;
        this.sessionQos1MsgQueue = sessionQos1MsgQueue;
        this.sessionQos2MsgQueue = sessionQos2MsgQueue;
        this.inflictWindowSize = inflictWindowSize;
        this.clientVersion = clientVersion;
        inflictSlots = new AtomicInteger(inflictWindowSize); // this should be configurable
    }


    /**
     * 接收到客户端发送过来的消息
     *
     * @param msg msg；
     */
    @Override
    public boolean processReceiveMsg(KernelPayloadMessage msg) throws IOException {
        status.set(SessionStatus.CONNECTED);
        return postOffice.processReceiverMsg(msg, this);
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
        doDrainQos1QueueToConnection();
        return true;
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

    @Override
    public void publishMsg(KernelPayloadMessage msg) {
        if (ObjectUtils.isEmpty(msg)) {
            return;
        }
        switch (msg.getQos()) {
            case AT_MOST_ONCE -> sendMsgAtQos0(msg);
            //这里发布的时候两个使用同样的处理方法即可
            case AT_LEAST_ONCE -> sendMsgAtQos1(msg);
            case EXACTLY_ONCE -> sendMsgAtQos2(msg);
            //TODO UDP 的转发逻辑还没有实现
            case UDP -> sendMsgAtUdp(msg);
            default -> logger.error("Not admissible");
        }
    }

    /**
     * QOS0
     *
     * @param msg msg
     */
    private void sendMsgAtQos0(KernelPayloadMessage msg) {
        connection.sendIfWritableElseDrop(msg);
    }

    /**
     * QOS1
     *
     * @param msg msg
     */
    private void sendMsgAtQos1(KernelPayloadMessage msg) {
        if (!connected() && isClean()) { //pushing messages to disconnected not clean session
            return;
        }

        boolean canSkipQos1Queue = sessionQos1MsgQueue.isEmpty() && inflictSlots.get() > 0 && connected() && connection.getChannel().isWritable();

        if (canSkipQos1Queue) {
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
            sessionQos1MsgQueue.offer(msg);
        }
    }

    /**
     * QOS2
     *
     * @param msg msg
     */
    private void sendMsgAtQos2(KernelPayloadMessage msg) {
        if (!connected() && isClean()) {
            //pushing messages to disconnected not clean session
            return;
        }

        // 判断是否可以跳过qos2的消息队列
        boolean canSkipQos2Queue = sessionQos2MsgQueue.isEmpty() && connected() && connection.getChannel().isWritable() && qos2SenderMsg == null;

        if (canSkipQos2Queue) {
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
            sessionQos2MsgQueue.offer(msg);
            //            drainQueueToConnection();
        }
    }

    /**
     * 发送UDP 协议消息
     *
     * @param msg 消息
     */
    private void sendMsgAtUdp(KernelPayloadMessage msg) {
        logger.error("Not admissible {}", "UDP");
    }

    /**
     * 获取滑动窗口
     *
     * @return 返回滑动窗口的对象
     */
    public Map<Short, KernelPayloadMessage> getQos1InflictWindow() {
        return qos1InflictWindow;
    }

    /**
     * 绑定连接
     *
     * @param connection 具体的连接对象
     */
    public void bind(AbstractConnection connection) {
        this.connection = connection;
//        Thread.startVirtualThread(this);
    }

    /**
     * 订阅主题
     *
     * @param subscriptions sbu
     * @return 返回订阅列表
     */
    @Override
    public List<Subscription> subscriptions(List<Subscription> subscriptions) {
        List<Subscription> subscriptionsResult = postOffice.subscriptions(this, subscriptions);
        List<String> subscriptionStr = subscriptionsResult.stream().map(subscription -> subscription.topicFilter.getValue()).toList();
        this.subTopicStr.addAll(subscriptionStr);
        return subscriptionsResult;
    }

    /**
     * 添加滑动窗口
     *
     * @param inflictWindows window
     */
    public void addQos1InflictWindow(Map<Short, KernelPayloadMessage> inflictWindows) {
        inflictWindows.forEach((packetId, msg) -> {
            inflictTimeouts.add(new InFlightPacket(packetId, FLIGHT_BEFORE_RESEND_MS));
            inflictSlots.decrementAndGet();
        });
    }

    /**
     * 推送队列中的消息
     */
    public void flushAllQueuedMessages() {
        // 清理Qos1 的消息
        doDrainQos1QueueToConnection();

        // 清理QOS2的消息
        drainQos2QueueToConnection();
    }

    /**
     * doDrainQueueToConnection
     */
    protected void doDrainQos1QueueToConnection() {
        reSendInflictNotAcked();
        boolean inflictHasSlotsAndConnectionIsUp = inflictSlots.get() > 0 && connected() && connection.getChannel().isWritable();
        while (!sessionQos1MsgQueue.isEmpty() && inflictHasSlotsAndConnectionIsUp) {
            KernelPayloadMessage msg = sessionQos1MsgQueue.poll();
            if (!ObjectUtils.isEmpty(msg)) {
                /// 重新开发发送 qos1 的消息。
                sendMsgAtQos1(msg);
            }
        }
    }

    /**
     * 重新发送没有收到回复的消息
     */
    public void reSendInflictNotAcked() {
        if (this.inflictTimeouts.size() == 0) {
            qos1InflictWindow.clear();
        } else {
            List<InFlightPacket> expired = new ArrayList<>(inflictWindowSize);
            this.inflictTimeouts.drainTo(expired);

            if (logger.isDebugEnabled()) {
                StringBuilder sb = new StringBuilder();
                expired.forEach(packet -> sb.append(packet.getPacketId()).append(", "));
                logger.debug("Resending {} in flight packets [{}]", expired.size(), sb);
            }

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


    /**
     * 推送qos2消息
     */
    protected void drainQos2QueueToConnection() {
        if (null == qos2SenderMsg) {
            KernelPayloadMessage msg = sessionQos2MsgQueue.poll();
            /// 一直找到正常的消息为止
            if (null == msg) {
                return;
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
        //fireWillMsg
        if (null != willMsg) {
            postOffice.fireWill(willMsg, this);
        }
        //update status
        boolean res = status.compareAndSet(SessionStatus.CONNECTED, SessionStatus.DISCONNECTING);
        if (!res) {
            logger.info("this status is SessionStatus.DISCONNECTING");
            return;
        }
        connection = null;
        willMsg = null;
        status.compareAndSet(SessionStatus.DISCONNECTING, SessionStatus.DISCONNECTED);
    }


    /**
     * 重新连接标志
     *
     * @return 成功与否
     */
    public Boolean markReConnectingStatus() {
        return status.compareAndSet(SessionStatus.DISCONNECTED, SessionStatus.CONNECTING);
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
        while (sessionQos1MsgQueue.size() > 0) {
            sessionQos1MsgQueue.poll();
        }

        while (sessionQos2MsgQueue.size() > 0) {
            sessionQos2MsgQueue.poll();
        }
    }


    public void markConnecting() {
        status.compareAndSet(SessionStatus.DISCONNECTED, SessionStatus.CONNECTING);
    }

    public Boolean completeConnection() {
        return status.compareAndSet(SessionStatus.CONNECTING, SessionStatus.CONNECTED);
    }


    public void closeImmediately() {
        if (!ObjectUtils.isEmpty(connection)) {
            connection.dropConnection();
        }
    }

    @Override
    public String toString() {
        return "Session {" + "clientId='" + clientId + '\'' + ", clean=" + clean + ", status=" + status + ", inflightSlots=" + inflictSlots + '}';
    }

    @Override
    public void run() {
        while (status.get().ordinal() <= SessionStatus.CONNECTING.ordinal() && connection.getChannel().isActive()) {
            //链接处于正常链接状态
//            System.out.println(clientId + ">>>>>>>>>>>> " + Thread.currentThread().getName() + Thread.currentThread().isVirtual());
            //处理收到的消息
//            processPublishIndexQueue();
            //处理发送中的消息
            flushAllQueuedMessages();

            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
        //链接处于异常状态，这里需要判断session 是否到期被清除，
        // 因为有的协议需要session 保存一定的超时时间，当超时
        //时间到了之后，才能正常处理过期调的 session
//        System.out.println(clientId + ">>>>>>>>>>>> 链接断开状态,关闭虚拟线程");
    }
}
