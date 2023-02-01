package io.store.persistence;

import io.netty.util.internal.ObjectUtil;
import io.octopus.kernel.config.IConfig;
import io.octopus.kernel.contants.BrokerConstants;
import io.octopus.kernel.kernel.repository.*;
import io.octopus.kernel.kernel.router.IRouterRegister;
import io.octopus.kernel.utils.ObjectUtils;
import io.store.persistence.h2.H2Builder;
import io.store.persistence.leveldb.LevelDbBuilder;
import io.store.persistence.memory.MemoryBuilder;
import io.store.persistence.memory.MemorySubscriptionsRepository;
import org.h2.mvstore.MVStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;

/**
 * @author chenxu
 * @version 1
 */
public class StoreCreateFactory implements IStoreCreateFactory {
    final Logger logger = LoggerFactory.getLogger(this.getClass());

    private final IConfig config;
    private final String persistencePath;
    private final DatabasesType dbType;
    private IStoreCreateFactory storeCreateFactoryImp;

    public StoreCreateFactory(IConfig config) throws IOException {
        this.config = config;
        this.persistencePath = config.getProperty(BrokerConstants.PERSISTENT_STORE_PROPERTY_NAME, "data/db/octopus_store.db");
        String dataBasesType = config.getProperty(BrokerConstants.DATABASES_TYPE, "");
        this.dbType = DatabasesType.getDB(dataBasesType);
        start();
    }

    @Override
    public void init() throws Exception {

    }

    @Override
    public void start() throws IOException {
        if (ObjectUtils.isEmpty(dbType)) {
            ObjectUtil.checkNotNull(dbType, "The database type is wrong. It must be one of H2 / leveldb / memory ");
            System.exit(1);
        }
        switch (dbType) {
            //H2
            case H2 -> {
                String h2Path = persistencePath + "h2/octopus_store.db";
                initPersistencePath(h2Path);
                storeCreateFactoryImp = new H2Builder(config).initStore(new MVStore.Builder().fileName(h2Path).autoCommitDisabled().open());
            }
            //levelDB
            case LEVELDB ->  storeCreateFactoryImp = new LevelDbBuilder(config, persistencePath + "levelDB/").initStore();

            //memory
            default -> storeCreateFactoryImp = new MemoryBuilder();
        }
        logger.info("start db {}", dbType);
    }

    @Override
    public void destroy() {

    }

    @Override
    public IndexQueueFactory createIndexQueueRepository() {
        return storeCreateFactoryImp.createIndexQueueRepository();
    }

    @Override
    public IRetainedRepository createIRetainedRepository() {
        return storeCreateFactoryImp.createIRetainedRepository();
    }

    @Override
    public ISubscriptionsRepository createISubscriptionsRepository() {
        //use memory store
        return new MemorySubscriptionsRepository();
//        return storeCreateFactoryImp.createISubscriptionsRepository();
    }

    @Override
    public IRouterRegister createIRouterRegister() {
        return storeCreateFactoryImp.createIRouterRegister();
    }

    @Override
    public IMsgQueue createIMsgQueueRepository() {
        return storeCreateFactoryImp.createIMsgQueueRepository();
    }


    private void initPersistencePath(String path) {
        logger.info("Configuring Using persistent store file, path: {}", path);
        if (ObjectUtils.isEmpty(path)) {
            throw new IllegalArgumentException("store path can't be null or empty");
        }
        File directory = new File(path);
        if (!directory.getParentFile().exists()) {
            directory.getParentFile().mkdirs();
        }
    }
}
