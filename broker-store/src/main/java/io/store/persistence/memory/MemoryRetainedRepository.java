package io.store.persistence.memory;

import io.handler.codec.mqtt.MqttPublishMessage;
import io.netty.buffer.ByteBuf;
import io.octopus.base.interfaces.IRetainedRepository;
import io.octopus.base.subscriptions.RetainedMessage;
import io.octopus.base.subscriptions.Topic;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/*
* In memory retained messages store
* */
public final class MemoryRetainedRepository implements IRetainedRepository {

    private final ConcurrentMap<Topic, RetainedMessage> storage = new ConcurrentHashMap<>();

    @Override
    public void cleanRetained(Topic topic) {
        storage.remove(topic);
    }

    @Override
    public void retain(Topic topic, MqttPublishMessage msg) {
        final ByteBuf payload = msg.content();
        byte[] rawPayload = new byte[payload.readableBytes()];
        payload.getBytes(0, rawPayload);
        final RetainedMessage toStore = new RetainedMessage(topic, msg.fixedHeader().qosLevel(), rawPayload);
        storage.put(topic, toStore);
    }

    @Override
    public boolean isEmpty() {
        return storage.isEmpty();
    }

    @Override
    public List<RetainedMessage> retainedOnTopic(String topic) {
        final Topic searchTopic = new Topic(topic);
        final List<RetainedMessage> matchingMessages = new ArrayList<>();
        for (Map.Entry<Topic, RetainedMessage> entry : storage.entrySet()) {
            final Topic scanTopic = entry.getKey();
            if (scanTopic.match(searchTopic)) {
                matchingMessages.add(entry.getValue());
            }
        }
        return matchingMessages;
    }
}
