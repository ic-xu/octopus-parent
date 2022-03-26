package io.octopus.base.interfaces;

import io.octopus.base.queue.MsgIndex;

import java.util.Map;
import java.util.Queue;

/**
 * @author user
 */
public interface IQueueRepository {

    /**
     * create a queue
     * @param clientId clientId
     * @param isClean
     * @return Queue<MsgIndex> queue
     */
    Queue<MsgIndex> createQueue(String clientId, boolean isClean);

    /**
     *
     * @return
     */
    Map<String, Queue<MsgIndex>> listAllQueues();

    /**
     * clenr
     * @param clientId clientId
     */
    void cleanQueue(String clientId);
}
