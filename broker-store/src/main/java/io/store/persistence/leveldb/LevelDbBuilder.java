package io.store.persistence.leveldb;

import io.octopus.kernel.config.IConfig;
import io.octopus.kernel.kernel.repository.*;
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
    public IndexQueueFactory createIndexQueueRepository() {
        return new LevelDbQueueFactory(db);
    }

    @Override
    public IRetainedRepository createIRetainedRepository() {
        return new MemoryRetainedRepository();
    }

    @Override
    public ISubscriptionsRepository createISubscriptionsRepository() {
        throw new RuntimeException("");
    }

    @Override
    public IRouterRegister createIRouterRegister() {
        throw new RuntimeException("");
    }

    @Override
    public IMsgQueue createIMsgQueueRepository() {
        throw new RuntimeException("");
    }


    @Override
    public void init() throws Exception {

    }

    @Override
    public void destroy() throws Exception {

    }
}
