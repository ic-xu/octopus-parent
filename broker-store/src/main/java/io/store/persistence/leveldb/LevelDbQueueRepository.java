package io.store.persistence.leveldb;

import io.octopus.kernel.kernel.message.KernelPayloadMessage;
import io.octopus.kernel.kernel.queue.MsgIndex;
import io.octopus.kernel.kernel.repository.IQueueRepository;
import io.store.persistence.memory.MemoryQueueRepository;
import org.iq80.leveldb.DB;

import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

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
    public Queue<KernelPayloadMessage> createQueue(String cli, boolean clean) {
//        return new LevelDBPersistentQueue(db,cli);
        return new ConcurrentLinkedQueue<>();
    }

    @Override
    public Map<String, Queue<KernelPayloadMessage>> listAllQueues() {
        return null;
    }

    @Override
    public void cleanQueue(String cli) {
        new LevelDBPersistentQueue(db,cli).clear();
    }
}
