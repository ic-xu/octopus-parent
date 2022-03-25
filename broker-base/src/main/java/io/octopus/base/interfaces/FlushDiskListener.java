package io.octopus.base.interfaces;


import io.handler.codec.mqtt.IMessage;

public interface FlushDiskListener {

    void flushDiskCall(Boolean result, IMessage message);
}
