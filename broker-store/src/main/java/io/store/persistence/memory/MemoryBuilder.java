package io.store.persistence.memory;

import io.octopus.kernel.kernel.repository.IQueueRepository;
import io.octopus.kernel.kernel.repository.IRetainedRepository;
import io.octopus.kernel.kernel.repository.IStoreCreateFactory;
import io.octopus.kernel.kernel.repository.ISubscriptionsRepository;
import io.octopus.kernel.kernel.router.IRouterRegister;

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

    @Override
    public void init() throws Exception {

    }

    @Override
    public void destroy() throws Exception {

    }
}
