package io.octopus.base.queue;

import io.octopus.base.checkpoint.CheckPoint;
import io.octopus.base.interfaces.LifeCycle;

/**
 * @author chenxu
 * @version 1
 */
public interface MsgQueue<E> extends LifeCycle {


    default void initialize(Object params) {
    }


    /**
     *
     * @param e message byteArray
     * @return the message index
     */
    StoreMsg<E> offer(E e);


    /**
     * Retrieves and removes the head of this queue,
     * or returns {@code null} if this queue is empty.
     *
     * @return the head of this queue, or {@code null} if this queue is empty
     */
    StoreMsg<E> poll();


    int size();


    /**
     * Retrieves and removes the offset message of this queue,
     * or returns {@code null} if this queue is empty.
     *
     * @param searchData index
     * @return E
     */
    StoreMsg<E> poll(SearchData searchData);


    /**
     * Force message to disk
     */
    void flushDisk();


    /**
     * create a checkPoint in the queue
     *
     * @return CheckPoint
     */
    CheckPoint wrapperCheckPoint();
}
