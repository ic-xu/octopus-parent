package io.octopus.persistence.memory;

import io.handler.codec.mqtt.MqttQoS;
import io.octopus.broker.subscriptions.Topic;
import io.octopus.broker.subscriptions.TopicRegister;
import io.octopus.broker.subscriptions.maptree.MemoryTopicRegister;
import io.octopus.persistence.ISubscriptionsRepository;
import io.octopus.broker.subscriptions.Subscription;
import io.octopus.utils.TopicUtils;

import java.util.*;

public class MemorySubscriptionsRepository implements ISubscriptionsRepository {

    private final TopicRegister rootTopicRegisterMap = new MemoryTopicRegister("ROOT");

    @Override
    public Set<Subscription> listAllSubscriptions() {
        return new HashSet<>();
    }

    @Override
    public void addNewSubscription(Subscription subscription) {
        rootTopicRegisterMap.registerTopic(subscription, TopicUtils.getTopicLevelArr(subscription.getTopicFilter().getValue()));
    }

    @Override
    public void removeSubscription(String topic, String clientID) {
        rootTopicRegisterMap.unRegisterTopic(new Subscription(clientID, new Topic(topic), MqttQoS.AT_MOST_ONCE), TopicUtils.getTopicLevelArr(topic));
    }

    @Override
    public Set<Subscription> retrieveSub(String topic) {
        return rootTopicRegisterMap.getSubscriptions(TopicUtils.getTopicLevelArr(topic));
    }
}
