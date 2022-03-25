package io.octopus.base.interfaces;

import io.octopus.base.queue.MsgIndex;

import java.util.Map;
import java.util.Queue;

public interface IQueueRepository {

    Queue<MsgIndex> createQueue(String cli, boolean clean);

    Map<String, Queue<MsgIndex>> listAllQueues();

    void cleanQueue(String cli);
}
