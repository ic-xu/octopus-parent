package io.store.persistence.memory;

import io.octopus.kernel.kernel.message.IMessage;
import io.octopus.kernel.kernel.repository.*;
import io.octopus.kernel.kernel.router.IRouterRegister;

/**
 * @author chenxu
 * @version 1
 */
public class MemoryBuilder implements IStoreCreateFactory {

    @Override
    public IndexQueueFactory createIndexQueueRepository() {
        return new MemoryQueueFactory();
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
    public IMsgQueue<IMessage> createIMsgQueueRepository() {
        return new MemberMsgQueue<>();
    }

    @Override
    public void init() throws Exception {

    }

    @Override
    public void destroy() throws Exception {

    }
}
