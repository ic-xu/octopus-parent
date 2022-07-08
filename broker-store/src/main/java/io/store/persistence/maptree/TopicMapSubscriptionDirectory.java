package io.store.persistence.maptree;


import io.octopus.kernel.kernel.contants.ConstantsTopics;
import io.octopus.kernel.kernel.repository.ISubscriptionsRepository;
import io.octopus.kernel.kernel.subscriptions.ISubscriptionsDirectory;
import io.octopus.kernel.kernel.subscriptions.Subscription;
import io.octopus.kernel.kernel.subscriptions.Topic;
import io.octopus.kernel.kernel.subscriptions.plugin.TopicsFilter;
import io.octopus.kernel.kernel.subscriptions.plugin.imp.DefaultTopics;
import io.octopus.kernel.kernel.subscriptions.plugin.imp.IntersectionTopics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

@SuppressWarnings("ALL")
public class TopicMapSubscriptionDirectory implements ISubscriptionsDirectory {


    private  final Logger logger = LoggerFactory.getLogger(TopicMapSubscriptionDirectory.class);


    public static AtomicInteger size = new AtomicInteger(0);


    private volatile ISubscriptionsRepository subscriptionsRepository;

//    private  TopicRegister rootTopicRegisterMap;


    private List<TopicsFilter> chains = new ArrayList<>();

    @Override
    public void init(ISubscriptionsRepository subscriptionsRepository) {
        logger.info("Initializing CTrie");

//        rootTopicRegisterMap = new MemoryTopicRegister("ROOT");

        logger.info("Initializing subscriptions store...");
        this.subscriptionsRepository = subscriptionsRepository;
        // reload any subscriptions persisted
        if (logger.isTraceEnabled()) {
            logger.trace("Reloading all stored subscriptions. SubscriptionTree = {}", dumpTree());
        }

//        for (Subscription subscription : this.subscriptionsRepository.listAllSubscriptions()) {
//            LOGGER.debug("Re-subscribing {}", subscription);
//            rootTopicRegisterMap.registerTopic(subscription, TopicUtils.getTopicLevelArr(subscription.topicFilter.getValue().trim()));
//        }
        if (logger.isTraceEnabled()) {
            logger.trace("Stored subscriptions have been reloaded. SubscriptionTree = {}", dumpTree());
        }

        initChain();
    }


    /**
     * process chain
     */
    public void initChain() {
        //default process
        chains.add(new DefaultTopics());

        //customer
        chains.add(new IntersectionTopics());
    }

    /**
     * Given a topic string return the clients subscriptions that matches it. Topic string can't
     * contain character # and + because they are reserved to listeners subscriptions, and not topic
     * publishing.
     *
     * @param topic to use fo searching matching subscriptions.
     * @return the list of matching subscriptions, or empty if not matching.
     */

    @Override
    public Set<Subscription> matchWithoutQosSharpening(Topic topic) {
        for (TopicsFilter chain : chains) {
            if (chain.support(topic))
                return chain.matchWithoutQosSharpening(subscriptionsRepository, topic);
        }
        return new HashSet<>();
    }


//    @Override
//    public Set<Subscription> matchWithoutQosSharpening(Topic topic) {
//        String in_topics = topic.getValue().replace("in topics", "").trim().replace("'", "")
//            .replace("\"", "").replaceAll("\\s+", " ");
//
//        String[] topicArr = in_topics.split("&&");
//        if (topicArr.length == 1) {
//            return rootTopicTreeMap.getSubscriptions(TopicUtils.getTopicLevelArr(topicArr[0].trim()));
//        }
//        ArrayList<Set<String>> subscriptions = new ArrayList<>(topicArr.length);
//        Map<String, Subscription> resultMap = new HashMap<>();
//        for (int i = 0; i < topicArr.length; i++) {
//            Topic topic1 = new Topic(topicArr[i].trim());
//            if (topic1.isValid()) {
//                HashSet<String> clientId = new HashSet<>();
//                for (Subscription sub : rootTopicTreeMap.getSubscriptions(TopicUtils.getTopicLevelArr(topic1.getValue()))) {
//                    clientId.add(sub.clientId);
//                    if (i == 0) {
//                        resultMap.put(sub.clientId, sub);
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

    @Override
    public Set<Subscription> matchQosSharpening(Topic topic, boolean isNeedBroadcasting) {
        final Set<Subscription> subscriptions = matchWithoutQosSharpening(topic);
        if (isNeedBroadcasting) {
            try {
//                Set<Subscription> sysBroadcasting = rootTopicRegisterMap.getChildren().get(ConstantsTopics.$SYS_BROADCASTING).getSubscriptions();
                Set<Subscription> sysBroadcasting = subscriptionsRepository.retrieveSub(ConstantsTopics.SYS_BROADCASTING);
                int random = ThreadLocalRandom.current().nextInt(sysBroadcasting.size());
                Object[] objects = sysBroadcasting.toArray();
                Subscription broadcastSubscription = (Subscription) objects[random];
                subscriptions.add(broadcastSubscription);
            } catch (Exception ignore) {
            }
        }

        Map<String, Subscription> subsGroupedByClient = new HashMap<>();
        for (Subscription sub : subscriptions) {
            Subscription existingSub = subsGroupedByClient.get(sub.getClientId());
            // update the selected subscriptions if not present or if has a greater qos
            if (existingSub == null || existingSub.qosLessThan(sub)) {
                subsGroupedByClient.put(sub.getClientId(), sub);
            }
        }
        return new HashSet<>(subsGroupedByClient.values());
    }

    @Override
    public void add(Subscription newSubscription) {
//        rootTopicRegisterMap.registerTopic(newSubscription, TopicUtils.getTopicLevelArr(newSubscription.topicFilter.getValue().trim()));
        subscriptionsRepository.addNewSubscription(newSubscription);
    }

    /**
     * Removes subscription from CTrie, adds TNode when the last client unsubscribes, then calls for cleanTomb in a
     * separate atomic CAS operation.
     *
     * @param topic    the subscription's topic to remove.
     * @param clientID the Id of client owning the subscription.
     */
    @Override
    public void removeSubscription(Topic topic, String clientID) {
//        rootTopicRegisterMap.unRegisterTopic(new Subscription(clientID, topic, MqttQoS.AT_MOST_ONCE), TopicUtils.getTopicLevelArr(topic.getValue()));
        this.subscriptionsRepository.removeSubscription(topic.getValue(), clientID);
    }

    @Override
    public int size() {
        return size.get();
    }

    @Override
    public String dumpTree() {
        return "";
    }

}
