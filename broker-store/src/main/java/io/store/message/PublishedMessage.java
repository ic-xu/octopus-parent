package io.store.message;

import io.handler.codec.mqtt.MqttQoS;
import io.netty.buffer.ByteBuf;
import io.octopus.base.subscriptions.Topic;

public class PublishedMessage extends EnqueuedMessage {

    final Topic topic;
    final MqttQoS publishingQos;
    final ByteBuf payload;

    public PublishedMessage(int packageId,Topic topic, MqttQoS publishingQos, ByteBuf payload) {
        super(packageId);
        this.topic = topic;
        this.publishingQos = publishingQos;
        this.payload = payload;
    }

    public Topic getTopic() {
        return topic;
    }

    public MqttQoS getPublishingQos() {
        return publishingQos;
    }

    public ByteBuf getPayload() {
        return payload;
    }

    @Override
    public void release() {
        payload.release();
    }

    @Override
    public void retain() {
        payload.retain();
    }

}