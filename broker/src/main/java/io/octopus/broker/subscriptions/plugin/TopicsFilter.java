package io.octopus.broker.subscriptions.plugin;

import io.octopus.broker.subscriptions.Subscription;
import io.octopus.broker.subscriptions.Topic;
import io.octopus.broker.subscriptions.TopicRegister;
import io.octopus.persistence.ISubscriptionsRepository;

import java.util.Set;


public interface TopicsFilter {

    boolean support(Topic topic);

    Set<Subscription> matchWithoutQosSharpening(ISubscriptionsRepository subscriptionsRepository, Topic topic);
}
