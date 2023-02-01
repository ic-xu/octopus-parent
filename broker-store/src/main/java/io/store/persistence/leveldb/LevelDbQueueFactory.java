package io.store.persistence.leveldb;

import io.octopus.kernel.kernel.queue.Index;
import io.octopus.kernel.kernel.repository.IndexQueueFactory;
import org.iq80.leveldb.DB;

import java.util.Map;
import java.util.Queue;

/**
 * @author chenxu
 * @version 1
 */
public class LevelDbQueueFactory implements IndexQueueFactory {

    private final DB db;

    public LevelDbQueueFactory(DB db) {
        this.db = db;
    }

    @Override
    public Queue<Index> createQueue(String cli, boolean clean) {
        return new LevelDBPersistentQueue(db,cli);
    }

    @Override
    public Map<String, Queue<Index>> listAllQueues() {
        return null;
    }

    @Override
    public void cleanQueue(String cli) {
        new LevelDBPersistentQueue(db,cli).clear();
    }
}
