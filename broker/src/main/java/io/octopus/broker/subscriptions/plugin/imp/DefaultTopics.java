package io.octopus.broker.subscriptions.plugin.imp;

import io.octopus.broker.subscriptions.Subscription;
import io.octopus.broker.subscriptions.Topic;
import io.octopus.broker.subscriptions.TopicRegister;
import io.octopus.persistence.ISubscriptionsRepository;
import io.octopus.utils.TopicUtils;
import io.octopus.broker.subscriptions.plugin.TopicsFilter;
import static io.octopus.contants.ConstantsTopics.and;
import static io.octopus.contants.ConstantsTopics.or;

import java.util.Set;

public class DefaultTopics implements TopicsFilter {


    @Override
    public boolean support(Topic topic) {
        String value = topic.getValue();
        return !value.contains(and) && !value.contains(or);
    }

    @Override
    public Set<Subscription> matchWithoutQosSharpening(ISubscriptionsRepository subscriptionsRepository, Topic topic) {
        return subscriptionsRepository.retrieveSub(topic.getValue());
    }

//    @Override
//    public Set<Subscription> matchWithoutQosSharpening(TopicRegister topicRegister, Topic topic) {
//        String in_topics = TopicUtils.formatTopics(topic);
//        return topicRegister.getSubscriptions(TopicUtils.getTopicLevelArr(in_topics));
//    }
}
