package io.octopus.kernel.kernel.repository;

import io.octopus.kernel.kernel.subscriptions.Subscription;

import java.util.Set;

public interface ISubscriptionsRepository {

    Set<Subscription> listAllSubscriptions();

    void addNewSubscription(Subscription subscription);

    void removeSubscription(String topic, String clientID);

    Set<Subscription> retrieveSub(String topic);

}
