package io.octopus.kernel.kernel.repository;

import io.octopus.kernel.kernel.message.KernelPayloadMessage;
import io.octopus.kernel.kernel.queue.MsgIndex;

import java.util.Map;
import java.util.Queue;

public interface IQueueRepository {

    Queue<KernelPayloadMessage> createQueue(String cli, boolean clean);

    Map<String, Queue<KernelPayloadMessage>> listAllQueues();

    void cleanQueue(String cli);
}
