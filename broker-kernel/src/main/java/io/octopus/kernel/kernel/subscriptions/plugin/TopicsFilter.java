package io.octopus.kernel.kernel.subscriptions.plugin;

import io.octopus.kernel.kernel.repository.ISubscriptionsRepository;
import io.octopus.kernel.kernel.subscriptions.Subscription;
import io.octopus.kernel.kernel.subscriptions.Topic;

import java.util.Set;


public interface TopicsFilter {

    /**
     * 是否支持改过滤器
     * @param topic t
     * @return boolean
     */
    boolean support(Topic topic);


    /**
     * 匹配器
     *
     * @param subscriptionsRepository 存储器
     * @param topic                   topic
     * @return set
     */
    Set<Subscription> matchWithoutQosSharpening(ISubscriptionsRepository subscriptionsRepository, Topic topic);
}
