
package io.store.persistence.h2;

import io.octopus.kernel.kernel.queue.Index;
import io.octopus.kernel.kernel.repository.IQueueRepository;
import org.h2.mvstore.MVStore;

import java.util.HashMap;
import java.util.Map;
import java.util.Queue;

public class H2QueueRepository implements IQueueRepository {

    private MVStore mvStore;

    public H2QueueRepository(MVStore mvStore) {
        this.mvStore = mvStore;
    }

    @Override
    public Queue<Index> createQueue(String cli, boolean clean) {
//        if (!clean) {
        return new H2PersistentQueue<>(mvStore, cli);
//        }
//        return new ConcurrentLinkedQueue<>();
    }

    @Override
    public void cleanQueue(String cli) {

    }

    @Override
    public Map<String, Queue<Index>> listAllQueues() {
        Map<String, Queue<Index>> result = new HashMap<>();
        mvStore.getMapNames().stream()
                .filter(name -> name.startsWith("queue_") && !name.endsWith("_meta"))
                .map(name -> name.substring("queue_".length()))
                .forEach(name -> result.put(name, new H2PersistentQueue(mvStore, name)));
        return result;
    }
}
