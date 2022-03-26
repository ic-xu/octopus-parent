package io.octopus.base.interfaces;


import io.handler.codec.mqtt.IMessage;


/**
 *
 * listener
 * @author user
 */
public interface FlushDiskListener {

    /**
     * flushDisk callby
     * @param result
     * @param message
     */
    void flushDiskCall(Boolean result, IMessage message);
}
