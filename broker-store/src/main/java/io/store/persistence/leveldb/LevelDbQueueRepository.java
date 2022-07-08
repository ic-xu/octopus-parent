package io.store.persistence.leveldb;

import io.octopus.kernel.kernel.queue.MsgIndex;
import io.octopus.kernel.kernel.repository.IQueueRepository;
import org.iq80.leveldb.DB;

import java.util.Map;
import java.util.Queue;

/**
 * @author chenxu
 * @version 1
 */
public class LevelDbQueueRepository implements IQueueRepository {

    private final DB db;

    public LevelDbQueueRepository(DB db) {
        this.db = db;
    }

    @Override
    public Queue<MsgIndex> createQueue(String cli, boolean clean) {
        return new LevelDBPersistentQueue(db,cli);
    }

    @Override
    public Map<String, Queue<MsgIndex>> listAllQueues() {
        return null;
    }

    @Override
    public void cleanQueue(String cli) {
        new LevelDBPersistentQueue(db,cli).clear();
    }
}
