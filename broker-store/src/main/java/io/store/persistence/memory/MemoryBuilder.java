package io.store.persistence.memory;

import io.octopus.base.interfaces.*;
import io.store.persistence.*;

/**
 * @author chenxu
 * @version 1
 */
public class MemoryBuilder implements IStoreCreateFactory {

    @Override
    public IQueueRepository createIQueueRepository() {
        return new MemoryQueueRepository();
    }

    @Override
    public IRetainedRepository createIRetainedRepository() {
        return new MemoryRetainedRepository();
    }

    @Override
    public ISubscriptionsRepository createISubscriptionsRepository() {
        return new MemorySubscriptionsRepository();
    }

    @Override
    public IRouterRegister createIRouterRegister() {
        return null;
    }
}
