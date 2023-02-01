package io.octopus.kernel.kernel;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.octopus.kernel.kernel.interceptor.PostOfficeNotifyInterceptor;
import io.octopus.kernel.kernel.message.*;
import io.octopus.kernel.kernel.queue.Index;
import io.octopus.kernel.kernel.repository.IMsgQueue;
import io.octopus.kernel.kernel.repository.IRetainedRepository;
import io.octopus.kernel.kernel.security.ReadWriteControl;
import io.octopus.kernel.kernel.subscriptions.ISubscriptionsDirectory;
import io.octopus.kernel.kernel.subscriptions.RetainedMessage;
import io.octopus.kernel.kernel.subscriptions.Subscription;
import io.octopus.kernel.kernel.subscriptions.Topic;
import io.octopus.kernel.utils.ObjectUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author chenxu
 * @version 1
 * @date 2022/7/12 10:21
 */
public class DefaultPostOffice implements IPostOffice {

    private final static Logger LOGGER = LoggerFactory.getLogger(DefaultPostOffice.class);
    private final List<String> adminUser = new ArrayList<>(4);


    private final ISubscriptionsDirectory subscriptionsDirectory;
    private final IRetainedRepository retainedRepository;
    private final ISessionResistor sessionResistor;
    private final List<PostOfficeNotifyInterceptor> interceptors;
    private final ReadWriteControl authorizator;

    private final IMsgQueue iMsgQueue;

    public DefaultPostOffice(IMsgQueue<IMessage> iMsgQueue, ISubscriptionsDirectory subscriptionsDirectory, IRetainedRepository retainedRepository,
                             ISessionResistor sessionResistor, List<PostOfficeNotifyInterceptor> interceptors, ReadWriteControl authorizator) {
        this.iMsgQueue = iMsgQueue;
        this.subscriptionsDirectory = subscriptionsDirectory;
        this.retainedRepository = retainedRepository;
        this.sessionResistor = sessionResistor;
        this.interceptors = interceptors;
        this.authorizator = authorizator;
    }

    /**
     * 收到消息
     * @param msg   消息体
     * @param fromSession 消息来源
     * @return 是否接收成功
     */
    @Override
    public Boolean processReceiverMsg(KernelPayloadMessage msg, ISession fromSession) throws IOException {
        /// 校验session 是否具有发布消息的全新啊
        Topic topic = new Topic(msg.getTopic());
        if (!authorizator.canWrite(topic, fromSession.getUsername(), fromSession.getClientId())) {
            LOGGER.error("MQTT client: {} is not authorized to publish on topic: {}", fromSession.getClientId(), topic);
            return false;
        }

        //处理刷盘逻辑
        Index index = processFlushDisk(msg);

        // 分发消息
        publish2Subscribers(topic, false, index);

        // 如果消息需要存储，则调用存储组件
        processRetainMsg(msg, topic);
        return true;
    }

    @Override
    public void internalPublish(KernelPayloadMessage msg) throws IOException {
        MsgQos qos = msg.getQos();
        Topic topic = new Topic(msg.getTopic());
        LOGGER.debug("Sending internal PUBLISH message Topic={}, qos={}", topic, qos);
        Index index = iMsgQueue.storeMsg(msg);

        publish2Subscribers(topic, false, index);
        if (!msg.isRetain()) {
            return;
        }
        // QoS == 0 && retain => clean old retained
        if (qos == MsgQos.AT_MOST_ONCE || msg.getPayload().readableBytes() == 0) {
            retainedRepository.cleanRetained(topic);
            return;
        }
        retainedRepository.retain(topic, msg);
    }

    /**
     * 订阅消息
     *
     * @param topic    topic
     * @param qos      qos
     * @param clientId clientId
     * @return boolean
     */
    @Override
    public boolean subscription(ISession fromSession, String topic, MsgQos qos, String clientId) {
        Subscription subscription = new Subscription(clientId, new Topic(topic), qos);
        List<Subscription> subscriptionsList = new ArrayList<>();
        subscriptionsList.add(subscription);
        return subscriptions(fromSession, subscriptionsList).size() > 0;
    }

    @Override
    public List<Subscription> subscriptions(ISession fromSession, List<Subscription> subscriptions) {
        authorizator.verifyTopicsReadAccess(fromSession.getClientId(), fromSession.getUsername(), subscriptions);
        List<Subscription> newSubscriptions = subscriptions.stream().filter(sub -> sub.getRequestedQos() != MsgQos.FAILURE).collect(Collectors.toList());
        //    newSubscriptions.forEach(subscription => subscriptions.add(subscription))

        // add the subscriptions to Session
        //    val session = sessionFactory.retrieve(clientId)
        // 订阅消息
        newSubscriptions.forEach(subscriptionsDirectory::add);

        // 发布保留的订阅消息
        publishRetainedMessagesForSubscriptions(fromSession.getClientId(), newSubscriptions);
        newSubscriptions.forEach(subscription -> {
            if (!ObjectUtils.isEmpty(interceptors)) {
                interceptors.forEach(interceptor -> interceptor.notifyTopicSubscribed(subscription, fromSession.getUsername()));
            }
        });
        return newSubscriptions;
    }

    @Override
    public void unSubscriptions(ISession fromSession, Set<String> topics) {
        String clientID = fromSession.getClientId();
        topics.forEach(t -> {
            Topic topic = new Topic(t);
            boolean validTopic = topic.isValid();
            if (!validTopic) { // close the connection, not valid topicFilter is a protocol violation
                fromSession.handleConnectionLost();
                LOGGER.warn("Topic filter is not valid. CId={}, topics: {}, offending topic filter: {}", clientID, topics, topic);
                return;
            }
            LOGGER.trace("Removing subscription. CId={}, topic={}", clientID, topic);
            subscriptionsDirectory.removeSubscription(topic, clientID);
            if (!ObjectUtils.isEmpty(interceptors)) {
                interceptors.forEach(interceptor -> interceptor.notifyTopicUnsubscribed(topic.toString(), clientID, fromSession.getUsername()));
            }
        });
    }

    @Override
    public void cleanSubscribe(ISession fromSession, Set<String> topicStrSet) {
        topicStrSet.forEach(topicStr -> {
            LOGGER.trace("Removing subscription. CId={}, topic={}", fromSession.getClientId(), topicStr);
            subscriptionsDirectory.removeSubscription(new Topic(topicStr), fromSession.getClientId());
        });
    }

    @Override
    public void fireWill(KernelPayloadMessage will, ISession session) {

    }

    @Override
    public void addAdminUser(String[] registerUser) {
        if (!ObjectUtils.isEmpty(registerUser)) {
            adminUser.addAll(Arrays.asList(registerUser));
        }
    }


    /**
     * 处理刷盘逻辑
     *
     * @param msg msg
     * @return
     */
    private Index processFlushDisk(KernelPayloadMessage msg) throws IOException {
       return iMsgQueue.storeMsg(msg);
    }


    /**
     * publish2Subscribers: publish message to ever one client
     *
     * @param index           msg
     * @param topic              topic
     * @param isNeedBroadcasting if send message to other broker
     */
    private void publish2Subscribers(Topic topic, Boolean isNeedBroadcasting, Index index) {
        Set<Subscription> topicMatchingSubscriptions = subscriptionsDirectory.matchQosSharpening(topic, isNeedBroadcasting);
        topicMatchingSubscriptions.forEach(sub -> {
            //处理 qos,按照两个中比较小的一个发送
            MsgQos qos = MsgQos.lowerQosToTheSubscriptionDesired(sub, index.qos());
            // 发送某一个
            publish2ClientId(sub.getClientId(), sub.getTopicFilter().getValue(), qos, index, false);
        });
    }


    /**
     * publish Message to a client by clientId
     *
     * @param clientId  clientId
     * @param topicName topic
     * @param qos       qos
     * @param index  storeMsg
     */
    private void publish2ClientId(String clientId, String topicName, MsgQos qos, Index index, Boolean directPublish) {
        ISession targetSession = this.sessionResistor.retrieve(clientId);
        boolean isSessionPresent = targetSession != null;
        if (isSessionPresent) {
            LOGGER.debug("Sending PUBLISH message to active subscriber CId: {}, topicFilter: {}, qos: {}", clientId, topicName, qos);
            // we need to retain because duplicate only copy r/w indexes and don't retain() causing refCnt = 0
            targetSession.sendMsgAtQos(index, directPublish);
        } else { // If we are, the subscriber disconnected after the subscriptions tree selected that session as a
            // destination.
            LOGGER.debug("PUBLISH to not yet present session. CId: {}, topicFilter: {}, qos: {}", clientId, topicName, qos);
        }
    }


    /**
     * store retainMsg
     *
     * @param msg   msg
     * @param topic topic
     */
    private void processRetainMsg(KernelPayloadMessage msg, Topic topic) {
        if (msg.isRetain()) {
            if (!msg.getPayload().isReadable()) {
                retainedRepository.cleanRetained(topic);
            } else if (msg.getQos() == MsgQos.AT_MOST_ONCE) {
                retainedRepository.cleanRetained(topic);
            } else {
                // before wasn't stored
                retainedRepository.retain(topic, msg);
            }
        }
    }


    /**
     * TODO 发布保留的订阅消息
      */
    private void publishRetainedMessagesForSubscriptions(String clientID, List<Subscription> newSubscriptions) {
        ISession targetSession = this.sessionResistor.retrieve(clientID);
        newSubscriptions.forEach(subscription -> {
            String topicFilter = subscription.getTopicFilter().toString();
            List<RetainedMessage> retainedMsgs = retainedRepository.retainedOnTopic(topicFilter);
            if (retainedMsgs.isEmpty()) { // not found
                //continue
                // todo: continue is not supported
            } else {
                retainedMsgs.forEach(retainedMsg -> {
                    MsgQos retainedQos = retainedMsg.qosLevel();
                    MsgQos qos = MsgQos.lowerQosToTheSubscriptionDesired(subscription, retainedQos);
                    ByteBuf payloadBuf = Unpooled.wrappedBuffer(retainedMsg.getPayload());
                    KernelPayloadMessage message = new KernelPayloadMessage((short) 2,0L,retainedMsg.getProperties(), qos, MsgRouter.TOPIC, retainedMsg.getTopic().getValue(), payloadBuf, true, PubEnum.PUBLISH);
                    // sendRetainedPublishOnSessionAtQos
                    Index index = null;
                    try {
                        index = iMsgQueue.storeMsg(message);
                        targetSession.sendMsgAtQos(index, false);
                    } catch (IOException e) {

                    }

                    //                targetSession.sendRetainedPublishOnSessionAtQos(retainedMsg.getTopic(), qos, payloadBuf);
                });
            }
        });

    }

}
