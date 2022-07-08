package io.octopus.kernel.kernel.session;

import io.octopus.kernel.kernel.message.KernelMsg;
import io.octopus.kernel.kernel.queue.StoreMsg;
import io.octopus.kernel.kernel.subscriptions.Subscription;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.Set;

/**
 * 负责用户会话信息、发送消息缓存、接收消息、把接收到的消息转发下层处理
 * @author chenxu
 * @version 1
 */
public interface ISession {

    /**
     * 接收到客户端发送过来的消息
     * @param msg msg；
     */
    boolean receivePublishMsg(KernelMsg msg);

    /**
     * 发送消息给到客户端
     * @param storeMsg 消息
     * @param directPublish 是否直接发送
     */
    void sendMsgAtQos(StoreMsg<KernelMsg> storeMsg, Boolean directPublish);

    /**
     * 绑定UDP端点
     * @param address address
     */
    void bindUdpInetSocketAddress(InetSocketAddress address);

    /**
     * 获取UDP 端点
     * @return address
     */
    InetSocketAddress getUdpInetSocketAddress();


    /**
     *  sub
     * @param subscriptions sbu
     */
    List<Subscription> subscriptions(List<Subscription> subscriptions);

    /**
     * subscription
     * @param topics sub
     */
    void unSubscriptions(Set<String> topics);

    /**
     * cleanSubscribe
     */
    void cleanSubscribe();

    /**
     * handleConnectionLost
     */
    void handleConnectionLost();

    /**
     * session of bound session`s username
     * @return
     */
    String getUsername();

    /**
     * session of bound session`s clientId
     * @return
     */
    String getClientId();


    /**
     * 获取订阅主题名集合
     * @return
     */
    Set<String> getSubTopicList();


}
