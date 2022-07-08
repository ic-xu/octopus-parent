package io.octopus.utils;

import io.handler.codec.mqtt.MqttSubscribeMessage;
import io.handler.codec.mqtt.MqttTopicSubscription;
import io.octopus.kernel.kernel.message.MsgQos;
import io.octopus.kernel.kernel.subscriptions.Subscription;
import io.octopus.kernel.kernel.subscriptions.Topic;

import java.util.ArrayList;
import java.util.List;

/**
 * @author chenxu
 * @version 1
 * @date 2022/1/24 2:47 下午
 */
public class SubscriptionUtils {

    /**
     * MqttSubscribeMessage convert subscription
     * @param clientId clientId
     * @param subscribeMessage subMsg
     * @return Subscriptions
     */
    public static List<Subscription> wrapperSubscription(String clientId, MqttSubscribeMessage subscribeMessage) {
        ArrayList<Subscription> subscriptions = new ArrayList<>();
        for (MqttTopicSubscription req : subscribeMessage.payload().topicSubscriptions()) {
            Topic topic = new Topic(req.topicName());
            int value = req.qualityOfService().value();

            Subscription subscription = new Subscription(clientId, topic, MsgQos.valueOf(value));
            subscriptions.add(subscription);
        }
        return subscriptions;
    }
}
