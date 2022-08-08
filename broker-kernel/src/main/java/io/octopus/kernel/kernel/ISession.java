package io.octopus.kernel.kernel;

import io.octopus.kernel.kernel.message.KernelPayloadMessage;
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
     * @return  boolean
     */
    boolean receiveMsg(KernelPayloadMessage msg);



    /**
     * 接收到对端发送过来的收到确认消息，Qos1的第二个包
     * @param ackPacketId msg；
     * @return boolean
     */
    Boolean receivePubAcK(Short ackPacketId);


    /**
     * 发布收到
     * @param recPacketId msg；
     */
    void receivePubRec(Short recPacketId);

    /**
     * 发布释放
     * @param relPacketId msg；
     * @return boolean
     */
    Boolean receivePubReL(Short relPacketId);


    /**
     * 发布完成
     * @param pubCompPacketId msg；
     * @return b
     */
    Boolean receivePubComp(Short pubCompPacketId);

    /**
     * 发送消息给到客户端
     * @param storeMsg 消息
     * @param directPublish 是否直接发送
     */
    void sendMsgAtQos(KernelPayloadMessage kernelMsg, Boolean directPublish);

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
     * @return string
     */
    String getUsername();

    /**
     * session of bound session`s clientId
     * @return string
     */
    String getClientId();


    /**
     * 获取订阅主题名集合
     * @return set
     */
    Set<String> getSubTopicList();


}
