package io.octopus.base.subscriptions.plugin.imp;

import io.octopus.base.interfaces.ISubscriptionsRepository;
import io.octopus.base.subscriptions.Subscription;
import io.octopus.base.subscriptions.Topic;
import io.octopus.base.subscriptions.plugin.TopicsFilter;
import io.octopus.base.utils.TopicUtils;

import java.util.*;

import static io.octopus.base.contants.ConstantsTopics.AND;
import static io.octopus.base.contants.ConstantsTopics.OR;

public class IntersectionTopics implements TopicsFilter {

    @Override
    public boolean support(Topic topic) {
        String value = topic.getValue();
        return value.contains(AND) && !value.contains(OR);
    }

    @Override
    public Set<Subscription> matchWithoutQosSharpening(ISubscriptionsRepository subscriptionsRepository, Topic topic) {
        String inTopics = TopicUtils.formatTopics(topic);
        String[] topicArr = inTopics.split(AND);
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
