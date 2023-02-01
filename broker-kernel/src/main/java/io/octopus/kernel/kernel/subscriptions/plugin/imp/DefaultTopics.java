package io.octopus.kernel.kernel.subscriptions.plugin.imp;

import io.octopus.kernel.contants.ConstantsTopics;
import io.octopus.kernel.kernel.repository.ISubscriptionsRepository;
import io.octopus.kernel.kernel.subscriptions.Subscription;
import io.octopus.kernel.kernel.subscriptions.Topic;
import io.octopus.kernel.kernel.subscriptions.plugin.TopicsFilter;

import java.util.Set;

public class DefaultTopics implements TopicsFilter {


    @Override
    public boolean support(Topic topic) {
        String value = topic.getValue();
        return !value.contains(ConstantsTopics.AND) && !value.contains(ConstantsTopics.OR);
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
