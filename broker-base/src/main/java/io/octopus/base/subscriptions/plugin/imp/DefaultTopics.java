package io.octopus.base.subscriptions.plugin.imp;

import io.octopus.base.interfaces.ISubscriptionsRepository;
import io.octopus.base.subscriptions.Subscription;
import io.octopus.base.subscriptions.Topic;
import io.octopus.base.subscriptions.plugin.TopicsFilter;

import java.util.Set;

import static io.octopus.base.contants.ConstantsTopics.AND;
import static io.octopus.base.contants.ConstantsTopics.OR;

public class DefaultTopics implements TopicsFilter {


    @Override
    public boolean support(Topic topic) {
        String value = topic.getValue();
        return !value.contains(AND) && !value.contains(OR);
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
