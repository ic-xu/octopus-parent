package io.store.persistence.memory;

import io.octopus.base.interfaces.IQueueRepository;
import io.octopus.base.queue.MsgIndex;

import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class MemoryQueueRepository implements IQueueRepository {

    @Override
    public Queue<MsgIndex> createQueue(String cli, boolean clean) {
        return new ConcurrentLinkedQueue<>();
    }

    @Override
    public Map<String, Queue<MsgIndex>> listAllQueues() {
        return null;
    }

    @Override
    public void cleanQueue(String cli) {

    }
}
