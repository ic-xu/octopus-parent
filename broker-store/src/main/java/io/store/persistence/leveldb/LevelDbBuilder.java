package io.store.persistence.leveldb;

import io.octopus.kernel.kernel.config.IConfig;
import io.octopus.kernel.kernel.repository.IQueueRepository;
import io.octopus.kernel.kernel.repository.IRetainedRepository;
import io.octopus.kernel.kernel.repository.IStoreCreateFactory;
import io.octopus.kernel.kernel.repository.ISubscriptionsRepository;
import io.octopus.kernel.kernel.router.IRouterRegister;
import io.store.persistence.memory.MemoryRetainedRepository;
import org.iq80.leveldb.DB;
import org.iq80.leveldb.DBFactory;
import org.iq80.leveldb.Options;
import org.iq80.leveldb.impl.Iq80DBFactory;

import java.io.File;
import java.io.IOException;

/**
 * @author chenxu
 * @version 1
 */
public class LevelDbBuilder implements IStoreCreateFactory {

    private final IConfig config;
    private final String persistencePath;
    private DB db ;

    private Integer writeBufferSize = 1024*1024*10;

    private final DBFactory factory;

    public LevelDbBuilder(IConfig config,String persistencePath) {
        this.config = config;
        this.persistencePath = persistencePath;
        factory  = new Iq80DBFactory();

    }

    public LevelDbBuilder initStore() throws IOException {
        Options options = new Options();
        options.createIfMissing(true);
        // todo block cache
        if (writeBufferSize != null) {
            options.writeBufferSize(writeBufferSize);
        }
        db = factory.open(new File(persistencePath), options);
        return this;
    }



    @Override
    public IQueueRepository createIQueueRepository() {
        return new LevelDbQueueRepository(db);
    }

    @Override
    public IRetainedRepository createIRetainedRepository() {
        return new MemoryRetainedRepository();
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
