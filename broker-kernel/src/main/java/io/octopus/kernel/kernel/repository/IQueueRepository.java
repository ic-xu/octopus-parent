package io.octopus.kernel.kernel.repository;

import io.octopus.kernel.kernel.queue.MsgIndex;

import java.util.Map;
import java.util.Queue;

public interface IQueueRepository {

    Queue<MsgIndex> createQueue(String cli, boolean clean);

    Map<String, Queue<MsgIndex>> listAllQueues();

    void cleanQueue(String cli);
}
