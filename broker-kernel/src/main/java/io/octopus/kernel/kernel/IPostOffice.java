package io.octopus.kernel.kernel;

import io.octopus.kernel.kernel.message.KernelPayloadMessage;
import io.octopus.kernel.kernel.message.MsgQos;
import io.octopus.kernel.kernel.subscriptions.Subscription;

import java.util.List;
import java.util.Set;

/**
 * 1）、接收 session的消息
 * 2）、校验session是否有消息发布权限，
 * 3）、如果消息需要存储，则调用存储组件
 * 4）、分发消息
 * 5）、订阅管理(订阅、取消订阅)，
 *
 * @author chenxu
 * @version 1
 * @date 2022/1/21 5:10 下午
 */
public interface IPostOffice {

    /**
     * 1）、接收 session的消息
     * 2）、校验session是否有消息发布权限，
     * 3）、如果消息需要存储，则调用存储组件
     * 4）、分发消息
     *
     * @param kernelPayloadMessage   消息体
     * @param fromSession 消息来源
     * @return 结果
     */
    Boolean processReceiverMsg(KernelPayloadMessage kernelPayloadMessage, ISession fromSession);

    /**
     * 内部直接发送，跳过对topic 和用户的校验，一般用作嵌入broker 或者集群间的内部发送数据
     *
     * @param msg msg
     */
    void internalPublish(KernelPayloadMessage msg);

    /**
     * 订阅消息
     *
     * @param fromSession 消息来源
     * @param topic       主题
     * @param qos         消息质量
     * @param clientId    clientId
     * @return 结果
     */
    boolean subscription(ISession fromSession, String topic, MsgQos qos, String clientId);


    /**
     * 订阅消息
     *
     * @param fromSession   消息来源
     * @param subscriptions
     * @return
     */
    List<Subscription> subscriptions(ISession fromSession, List<Subscription> subscriptions);


    /**
     * 取消订阅
     *
     * @param fromSession session
     * @param topics      topic
     * @return boolean
     */
    void unSubscriptions(ISession fromSession, Set<String> topics);

    /**
     * 清除订阅
     *
     * @param fromSession session
     * @param topics      topic
     */
    void cleanSubscribe(ISession fromSession, Set<String> topics);


    /**
     * 释放遗嘱消息
     *
     * @param will
     * @param session
     */
    void fireWill(KernelPayloadMessage will, ISession session);

    /**
     *
     * @param registerUser
     */
    void addAdminUser(String[] registerUser);

}
