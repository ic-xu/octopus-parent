package io.octopus.persistence;

import io.octopus.broker.subscriptions.Subscription;

import java.util.List;
import java.util.Set;

public interface ISubscriptionsRepository {

    Set<Subscription> listAllSubscriptions();

    void addNewSubscription(Subscription subscription);

    void removeSubscription(String topic, String clientID);

    Set<Subscription> retrieveSub(String topic);
}
