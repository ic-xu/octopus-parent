package io.store.persistence.h2;

import io.octopus.kernel.kernel.subscriptions.Subscription;
import org.h2.mvstore.MVMap;

import java.util.HashSet;
import java.util.Set;

public class H2TopicTree {

    /**
     * 延迟初始化
     */
    private MVMap<String, Set<Subscription>> mvMap;

    public H2TopicTree(MVMap<String, Set<Subscription>> mvMap) {
        this.mvMap = mvMap;
    }

    public void registerTopic(Subscription subscription, String topics) {
        Set<Subscription> subscriptions = mvMap.computeIfAbsent(topics, (key) -> new HashSet<>());
        subscriptions.add(subscription);

    }


    public void unRegisterTopic(Subscription subscription, String topics) {
        Set<Subscription> subscriptions = mvMap.get(topics);
        if(null!=subscriptions){
            subscriptions.remove(subscription);
        }
    }


    public Set<Subscription> getSubscriptions(String topics) {
        return mvMap.computeIfAbsent(topics,(key)->new HashSet<>());
    }


    public void cleanChildren(String topic) {
      mvMap.remove(topic);
    }





}
