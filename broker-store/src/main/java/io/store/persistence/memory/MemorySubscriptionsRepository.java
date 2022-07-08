package io.store.persistence.memory;

import io.octopus.kernel.kernel.message.MsgQos;
import io.octopus.kernel.kernel.repository.ISubscriptionsRepository;
import io.octopus.kernel.kernel.subscriptions.Subscription;
import io.octopus.kernel.kernel.subscriptions.Topic;
import io.octopus.kernel.kernel.subscriptions.TopicRegister;
import io.octopus.kernel.utils.TopicUtils;
import io.store.persistence.maptree.MemoryTopicRegister;

import java.util.HashSet;
import java.util.Set;

/**
 * @author 陈旭
 */
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
    public void removeSubscription(String topic, String clientId) {
        rootTopicRegisterMap.unRegisterTopic(new Subscription(clientId, new Topic(topic), MsgQos.AT_MOST_ONCE), TopicUtils.getTopicLevelArr(topic));
    }

    @Override
    public Set<Subscription> retrieveSub(String topic) {
        return rootTopicRegisterMap.getSubscriptions(TopicUtils.getTopicLevelArr(topic));
    }
}
