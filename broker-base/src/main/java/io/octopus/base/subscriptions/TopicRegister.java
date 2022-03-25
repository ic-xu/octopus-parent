package io.octopus.base.subscriptions;

import java.util.Map;
import java.util.Set;

public interface TopicRegister {

    /**
     * register subscription
     * @param subscription sub
     * @param topics topic
     */
    void registerTopic(Subscription subscription, String... topics);

    /**
     * unRegister subscription
     * @param subscription sub
     * @param topics  top
     */
    void unRegisterTopic(Subscription subscription, String... topics);

    /**
     * get sub
     * @param topics topic
     * @return set
     */
    Set<Subscription> getSubscriptions(String... topics);


    /**
     * get children
     * @return TopicTree
     */
    Map<String, TopicRegister> getChildren();


    void cleanChildren();

    void cleanSubscriptions();

    String getTopicStringName();

}
