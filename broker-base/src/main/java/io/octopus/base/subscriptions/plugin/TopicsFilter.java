package io.octopus.base.subscriptions.plugin;

import io.octopus.base.interfaces.ISubscriptionsRepository;
import io.octopus.base.subscriptions.Subscription;
import io.octopus.base.subscriptions.Topic;

import java.util.Set;


public interface TopicsFilter {

    boolean support(Topic topic);

    Set<Subscription> matchWithoutQosSharpening(ISubscriptionsRepository subscriptionsRepository, Topic topic);
}
