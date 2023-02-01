package io.store.persistence.h2;

import io.octopus.kernel.config.IConfig;
import io.octopus.kernel.kernel.message.IMessage;
import io.octopus.kernel.kernel.repository.*;
import io.octopus.kernel.kernel.router.IRouterRegister;
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

    public IndexQueueFactory queueRepository() {
        return new H2IndexQueueFactory(mvStore);
    }

    public IRetainedRepository retainedRepository() {
        return new H2RetainedRepository(mvStore);
    }

    @Override
    public IndexQueueFactory createIndexQueueRepository() {
        return queueRepository();
    }

    @Override
    public IRetainedRepository createIRetainedRepository() {
        return retainedRepository();
    }

    @Override
    public ISubscriptionsRepository createISubscriptionsRepository() {
      throw  new RuntimeException("该方法还未实现");
    }

    @Override
    public IRouterRegister createIRouterRegister() {
        throw  new RuntimeException("该方法还未实现");
    }

    @Override
    public H2MsgQueue<IMessage> createIMsgQueueRepository() {
        return  new H2MsgQueue<>(mvStore);
    }

    @Override
    public void init() throws Exception {

    }

    @Override
    public void destroy() throws Exception {

    }
}
