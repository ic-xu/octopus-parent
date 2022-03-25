package io.octopus.persistence.h2;

import io.octopus.contants.BrokerConstants;
import io.octopus.persistence.IQueueRepository;
import io.octopus.persistence.IRetainedRepository;
import io.octopus.persistence.ISubscriptionsRepository;
import io.octopus.broker.config.IConfig;
import org.h2.mvstore.MVStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class H2Builder {

    private static final Logger LOGGER = LoggerFactory.getLogger(H2Builder.class);

    private final String storePath;
    private final int autosaveInterval; // in seconds
    private final ScheduledExecutorService scheduler;
    private MVStore mvStore;

    public H2Builder(IConfig props, ScheduledExecutorService scheduler) {
        this.storePath = props.getProperty(BrokerConstants.PERSISTENT_STORE_PROPERTY_NAME, "");
        final String autosaveProp = props.getProperty(BrokerConstants.AUTOSAVE_INTERVAL_PROPERTY_NAME, "30");
        this.autosaveInterval = Integer.parseInt(autosaveProp);
        this.scheduler = scheduler;
    }

    @SuppressWarnings("FutureReturnValueIgnored")
    public H2Builder initStore() {
        LOGGER.info("Initializing H2 store");
        if (storePath == null || storePath.isEmpty()) {
            throw new IllegalArgumentException("H2 store path can't be null or empty");
        }
        mvStore = new MVStore.Builder()
            .fileName(storePath)
            .autoCommitDisabled()
            .open();

        LOGGER.trace("Scheduling H2 commit task");
        scheduler.scheduleWithFixedDelay(() -> {
            LOGGER.trace("Committing to H2");
            mvStore.commit();
        }, autosaveInterval, autosaveInterval, TimeUnit.SECONDS);
        return this;
    }

    public ISubscriptionsRepository subscriptionsRepository() {
        return new H2SubscriptionsRepository(mvStore);
    }

    public void closeStore() {
        mvStore.close();
    }

    public IQueueRepository queueRepository() {
        return new H2QueueRepository(mvStore);
    }

    public IRetainedRepository retainedRepository() {
        return new H2RetainedRepository(mvStore);
    }
}
