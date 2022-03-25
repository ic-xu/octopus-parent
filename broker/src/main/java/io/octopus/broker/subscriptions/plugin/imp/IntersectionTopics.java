package io.octopus.broker.subscriptions.plugin.imp;

import io.octopus.broker.subscriptions.Subscription;
import io.octopus.broker.subscriptions.Topic;
import io.octopus.broker.subscriptions.TopicRegister;
import io.octopus.persistence.ISubscriptionsRepository;
import io.octopus.utils.TopicUtils;
import io.octopus.broker.subscriptions.plugin.TopicsFilter;

import java.util.*;

import static io.octopus.contants.ConstantsTopics.and;
import static io.octopus.contants.ConstantsTopics.or;

public class IntersectionTopics implements TopicsFilter {

    @Override
    public boolean support(Topic topic) {
        String value = topic.getValue();
        return value.contains(and) && !value.contains(or);
    }

    @Override
    public Set<Subscription> matchWithoutQosSharpening(ISubscriptionsRepository subscriptionsRepository, Topic topic) {
        String in_topics = TopicUtils.formatTopics(topic);
        String[] topicArr = in_topics.split(and);
        ArrayList<Set<String>> subscriptions = new ArrayList<>(topicArr.length);
        Map<String, Subscription> resultMap = new HashMap<>();
        for (int i = 0; i < topicArr.length; i++) {
            Topic topic1 = new Topic(topicArr[i].trim());
            if (topic1.isValid()) {
                HashSet<String> clientId = new HashSet<>();
                for (Subscription sub : subscriptionsRepository.retrieveSub(topic1.getValue())) {
                    clientId.add(sub.getClientId());
                    if (i == 0) {
                        resultMap.put(sub.getClientId(), sub);
                    }
                }
                subscriptions.add(clientId);
            }
        }
        Set<String> resultSet = subscriptions.get(0);
        if (subscriptions.size() > 1) {
            for (int i = 1; i < subscriptions.size(); i++) {
                resultSet.retainAll(subscriptions.get(i));
            }
        }
        Set<Subscription> result = new HashSet<>();
        for (String clientId : resultSet) {
            result.add(resultMap.get(clientId));
        }
        return result;
    }

//    @Override
//    public Set<Subscription> matchWithoutQosSharpening(TopicRegister topicRegister, Topic topic) {
//        String in_topics = TopicUtils.formatTopics(topic);
//        String[] topicArr = in_topics.split(and);
//        ArrayList<Set<String>> subscriptions = new ArrayList<>(topicArr.length);
//        Map<String, Subscription> resultMap = new HashMap<>();
//        for (int i = 0; i < topicArr.length; i++) {
//            Topic topic1 = new Topic(topicArr[i].trim());
//            if (topic1.isValid()) {
//                HashSet<String> clientId = new HashSet<>();
//                for (Subscription sub : topicRegister.getSubscriptions(TopicUtils.getTopicLevelArr(topic1.getValue()))) {
//                    clientId.add(sub.getClientId());
//                    if (i == 0) {
//                        resultMap.put(sub.getClientId(), sub);
//                    }
//                }
//                subscriptions.add(clientId);
//            }
//        }
//        Set<String> resultSet = subscriptions.get(0);
//        if (subscriptions.size() > 1) {
//            for (int i = 1; i < subscriptions.size(); i++) {
//                resultSet.retainAll(subscriptions.get(i));
//            }
//        }
//        Set<Subscription> result = new HashSet<>();
//        for (String clientId : resultSet) {
//            result.add(resultMap.get(clientId));
//        }
//        return result;
//    }
}
