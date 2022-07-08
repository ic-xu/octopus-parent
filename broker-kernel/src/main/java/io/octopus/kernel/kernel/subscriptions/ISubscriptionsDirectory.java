package io.octopus.kernel.kernel.subscriptions;

import io.octopus.kernel.kernel.repository.ISubscriptionsRepository;

import java.util.Set;

/**
 * @author user
 */
public interface ISubscriptionsDirectory {

    void init(ISubscriptionsRepository sessionsRepository);

    Set<Subscription> matchWithoutQosSharpening(Topic topic);

    Set<Subscription> matchQosSharpening(Topic topic,boolean isNeedBroadcasting);

    void add(Subscription newSubscription);

    void removeSubscription(Topic topic, String clientID);

    int size();

    String dumpTree();
}
