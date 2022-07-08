package io.store.persistence.maptree;
import io.octopus.kernel.kernel.subscriptions.Subscription;
import io.octopus.kernel.kernel.subscriptions.TopicRegister;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author user
 */
public class MemoryTopicRegister implements TopicRegister {

    private final String topicStringName;

    private Set<Subscription> subscriptions;

    private Map<String, TopicRegister> children;


    @Override
    public Map<String, TopicRegister> getChildren() {
        return children;
    }


    public MemoryTopicRegister(String topicStringName) {
        this.topicStringName = topicStringName;
    }

    @Override
    public String getTopicStringName() {
        return topicStringName;
    }

    @Override
    public void cleanSubscriptions() {
        if (subscriptions != null) {
            this.subscriptions.clear();
            this.subscriptions = null;
        }
    }

    @Override
    public void cleanChildren() {
        if (this.children != null) {
            this.children.clear();
            this.children = null;
        }
    }

    @Override
    public void registerTopic(Subscription subscription, String... topics) {

        if (topics.length < 1) {
            if (null == subscriptions){
                subscriptions = ConcurrentHashMap.newKeySet();
            }
            boolean add = subscriptions.add(subscription);
            if (add) {
                TopicMapSubscriptionDirectory.size.incrementAndGet();
            }
        } else {
            String remove = topics[0].trim();
            String[] topicsRetain = Arrays.copyOfRange(topics, 1, topics.length);
            if (null == children) {
                children = new ConcurrentHashMap<>();
            }
            TopicRegister topicRegister = children.get(remove);
            if (topicRegister == null) {
                topicRegister = new MemoryTopicRegister(remove);
            }
            topicRegister.registerTopic(subscription, topicsRetain);
            children.put(remove, topicRegister);

        }
    }

    /**
     * unRegisterTopic topics
     *
     * @param subscription s
     * @param topics       topic
     */
    @Override
    public void unRegisterTopic(Subscription subscription, String... topics) {

        if (topics.length < 1) {
        } else if (topics.length == 1) {
            if (null == children) {
                return;
            }
            TopicRegister topicRegister = children.get(topics[0]);
            if (topicRegister != null) {
                Set<Subscription> subscriptionsTmp = topicRegister.getSubscriptions();
                if (null != subscriptionsTmp) {
                    boolean remove = subscriptionsTmp.remove(subscription);
                    if (remove) {
                        TopicMapSubscriptionDirectory.size.decrementAndGet();
                    }
                    if (subscriptionsTmp.size() == 0) {
                        if (null == topicRegister.getChildren() || topicRegister.getChildren().size() == 0) {
                            topicRegister.cleanChildren();
                        }
                        topicRegister.cleanSubscriptions();
                    }
                }
                if (null == topicRegister.getChildren() && null == topicRegister.getSubscriptions()) {
                    children.remove(topics[0]);
                }
                if (null == children || children.size() == 0) {
                    children = null;
                }
            }

//            subscriptions.remove(subscription);
        } else {
            String topicRetain = topics[0];
            TopicRegister topicRegister = children.get(topicRetain);
            if (null != topicRegister) {
                String[] topicsRetain = Arrays.copyOfRange(topics, 1, topics.length);
                topicRegister.unRegisterTopic(subscription, topicsRetain);
                if (null == topicRegister.getChildren() && null == topicRegister.getSubscriptions()) {
                    children.remove(topicRetain);
                }
            }
        }
    }


    @Override
    public Set<Subscription> getSubscriptions(String... topics) {
        if (topics.length == 0) {
            return subscriptions;
        }

        Set<Subscription> subscriptionsResult = new HashSet<>();
        //表示最后一层
        if (topics.length <= 1) {
            if (children != null) {
                /** 精确匹配的情况 */
                TopicRegister topicRegister = children.get(topics[0].trim());
                if (null != topicRegister) {
                    Set<Subscription> subscriptions = topicRegister.getSubscriptions();
                    if (null != subscriptions){
                        subscriptionsResult.addAll(subscriptions);
                    }

                    if (null != topicRegister.getChildren()) {
                        /** 当前层级下的多层结构 */
                        TopicRegister multiLevelMemoryTopicRegister = topicRegister.getChildren().get("#");
                        if (null != multiLevelMemoryTopicRegister) {
                            Set<Subscription> multiLevelSubscription = multiLevelMemoryTopicRegister.getSubscriptions();
                            if (null != multiLevelSubscription){
                                subscriptionsResult.addAll(multiLevelSubscription);
                            }
                        }
                    }
                }

                /** 订阅当前层级的情况 + */
                TopicRegister currentLevel = children.get("+");
                if (null != currentLevel) {
                    Set<Subscription> subscriptions = currentLevel.getSubscriptions();
                    if (null != subscriptions){
                        subscriptionsResult.addAll(subscriptions);
                    }
                }

                /** 订阅当前层级下所有的情况 # */
                TopicRegister currentLevelAll = children.get("#");
                if (null != currentLevelAll) {
                    Set<Subscription> subscriptions = currentLevelAll.getSubscriptions();
                    if (null != subscriptions){
                        subscriptionsResult.addAll(subscriptions);
                    }
                }
            }
            return subscriptionsResult;
        }

        //订阅当前主题一下的情况 #
        TopicRegister currentTopicAll = children == null ? null : children.get("#");
        if (null != currentTopicAll) {
            subscriptionsResult.addAll(currentTopicAll.getSubscriptions());
        }

        //剩下层次数组
        String[] topicsRetain = Arrays.copyOfRange(topics, 1, topics.length);

        //不包含通配符情况
        String topicString = topics[0];
        TopicRegister memoryTopicRegister = children == null ? null : children.get(topicString);
        if (memoryTopicRegister != null) {
            subscriptionsResult.addAll(memoryTopicRegister.getSubscriptions(topicsRetain));
        }

        // 含有+ 通配符情况
        TopicRegister currentLevelMemoryTopicRegister = children == null ? null : children.get("+");
        if (currentLevelMemoryTopicRegister != null) {
            subscriptionsResult.addAll(currentLevelMemoryTopicRegister.getSubscriptions(topicsRetain));
        }
        return subscriptionsResult;
    }
}
