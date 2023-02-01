package io.store.persistence.memory;

import io.octopus.kernel.kernel.queue.Index;
import io.octopus.kernel.kernel.repository.IndexQueueFactory;

import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class MemoryQueueFactory implements IndexQueueFactory {

    @Override
    public Queue<Index> createQueue(String cli, boolean clean) {
        return new ConcurrentLinkedQueue<>();
    }

    @Override
    public Map<String, Queue<Index>> listAllQueues() {
        return null;
    }

    @Override
    public void cleanQueue(String cli) {

    }
}
