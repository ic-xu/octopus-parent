package io.store.persistence.memory;

import io.handler.codec.mqtt.MqttQoS;
import io.octopus.base.interfaces.ISubscriptionsRepository;
import io.octopus.base.subscriptions.Subscription;
import io.octopus.base.subscriptions.Topic;
import io.octopus.base.subscriptions.TopicRegister;
import io.store.persistence.maptree.MemoryTopicRegister;
import io.octopus.base.utils.TopicUtils;

import java.util.HashSet;
import java.util.Set;

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
