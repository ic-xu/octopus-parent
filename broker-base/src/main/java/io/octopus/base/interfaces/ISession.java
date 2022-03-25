package io.octopus.base.interfaces;

import io.octopus.base.message.Message;
import io.octopus.base.subscriptions.Subscription;

import java.net.InetSocketAddress;

/**
 * @author chenxu
 * @version 1
 */
public interface ISession {

    /**
     * 接收到一个消息
     * @param msg msg；
     */
    boolean receiveMessage(Message msg);

    /**
     * 发送消息
     * @param msg 消息
     * @return 是否发送成功
     */
    boolean sendMessage(Message msg);


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
     * @param subscription sbu
     */
    void subscription(Subscription subscription);

    /**
     * subscription
     * @param subscription sub
     */
    void unSubscription(Subscription subscription);


     int nextPacketId();

}
