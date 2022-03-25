package io.store.persistence.h2;

import io.octopus.base.config.IConfig;
import io.octopus.base.interfaces.*;
import io.store.persistence.*;
import org.h2.mvstore.MVStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class H2Builder implements IStoreCreateFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger(H2Builder.class);

    private MVStore mvStore;

    public H2Builder(IConfig props) {

    }

    @SuppressWarnings("FutureReturnValueIgnored")
    public H2Builder initStore(MVStore mvStore) {
       this. mvStore = mvStore;
        return this;
    }

    public MVStore getMvStore() {
        return mvStore;
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

    @Override
    public IQueueRepository createIQueueRepository() {
        return queueRepository();
    }

    @Override
    public IRetainedRepository createIRetainedRepository() {
        return retainedRepository();
    }

    @Override
    public ISubscriptionsRepository createISubscriptionsRepository() {
        return null;
    }

    @Override
    public IRouterRegister createIRouterRegister() {
        return null;
    }
}
