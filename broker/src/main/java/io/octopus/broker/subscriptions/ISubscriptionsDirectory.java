package io.octopus.broker.subscriptions;

import io.octopus.persistence.ISubscriptionsRepository;

import java.util.Set;

public interface ISubscriptionsDirectory {

    void init(ISubscriptionsRepository sessionsRepository);

    Set<Subscription> matchWithoutQosSharpening(Topic topic);

    Set<Subscription> matchQosSharpening(Topic topic,boolean isNeedBroadcasting);

    void add(Subscription newSubscription);

    void removeSubscription(Topic topic, String clientID);

    int size();

    String dumpTree();
}
