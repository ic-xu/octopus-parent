package io.octopus.kernel.kernel.repository;

import io.octopus.kernel.kernel.queue.Index;

import java.util.Map;
import java.util.Queue;

public interface IQueueRepository {

    Queue<Index> createQueue(String cli, boolean clean);

    Map<String, Queue<Index>> listAllQueues();

    void cleanQueue(String cli);
}
