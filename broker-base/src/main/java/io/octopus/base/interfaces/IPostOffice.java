package io.octopus.base.interfaces;

import io.octopus.base.message.Message;
import io.octopus.base.message.MessageQos;

/**
 * @author chenxu
 * @version 1
 * @date 2022/1/21 5:10 下午
 */
public interface IPostOffice {

    /**
     * 分发消息
     * @param message msg
     */
    void dispatchMessage(Message message);

    /**
     * 点对点发送任意消息
     * @param msg msg
     * @return boolean
     */
    boolean sendMessage(Object msg,String clientId);

    /**
     * 订阅消息
     * @param topic topic
     * @param qos qos
     * @param clientId clientId
     * @return boolean
     */
    boolean subscription(String topic, MessageQos qos,String clientId);


    /**
     * 取消订阅
     * @param topic topic
     * @param clientId client
     * @return boolean
     */
    boolean unSubscription(String topic,String clientId);
}
