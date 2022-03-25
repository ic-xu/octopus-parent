package io.handler.codec.mqtt;

/**
 * @author chenxu
 * @version 1
 */
public interface IMessage {

    long getMessageId();


    void setMessageId(long messageId);
}
