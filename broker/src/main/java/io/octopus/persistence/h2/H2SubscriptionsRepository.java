package io.octopus.persistence.h2;

import io.octopus.persistence.ISubscriptionsRepository;
import io.octopus.broker.subscriptions.Subscription;
import org.h2.mvstore.Cursor;
import org.h2.mvstore.MVMap;
import org.h2.mvstore.MVStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class H2SubscriptionsRepository implements ISubscriptionsRepository {

    private static final Logger LOGGER = LoggerFactory.getLogger(H2SubscriptionsRepository.class);
    private static final String SUBSCRIPTIONS_MAP = "subscriptions";

    private MVMap<String, Set<Subscription>> subscriptions;

    H2SubscriptionsRepository(MVStore mvStore) {
        this.subscriptions = mvStore.openMap(SUBSCRIPTIONS_MAP);
    }

    @Override
    public Set<Subscription> listAllSubscriptions() {
        LOGGER.debug("Retrieving existing subscriptions");

        Set<Subscription> results = new HashSet<>();
        Cursor<String, Set<Subscription>> mapCursor = subscriptions.cursor(null);
        while (mapCursor.hasNext()) {
            results.addAll(mapCursor.getValue());
        }
        LOGGER.debug("Loaded {} subscriptions", results.size());
        return results;
    }

    @Override
    public void addNewSubscription(Subscription subscription) {
        subscriptions.computeIfAbsent(subscription.getTopicFilter().getValue(), (key) -> new HashSet<>()).add(subscription);
    }

    @Override
    public void removeSubscription(String topicFilter, String clientID) {
        Set<Subscription> subscriptions = this.subscriptions.get(topicFilter);
        if (null != subscriptions)
            subscriptions.forEach(sub -> {
                if (sub.getClientId().equals(clientID)) {
                    subscriptions.remove(sub);
                }
            });
    }

    @Override
    public Set<Subscription> retrieveSub(String topic) {
        return subscriptions.computeIfAbsent(topic, (key) -> new HashSet<>());
    }
}
