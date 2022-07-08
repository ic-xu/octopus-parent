package io.octopus.kernel.kernel.message;

import io.netty.buffer.ByteBuf;
import io.octopus.kernel.kernel.subscriptions.Topic;

public class PublishedMessage extends EnqueuedMessage {

    final Topic topic;
    final MsgQos publishingQos;
    final ByteBuf payload;

    public PublishedMessage(int packageId,Topic topic, MsgQos publishingQos, ByteBuf payload) {
        super(packageId);
        this.topic = topic;
        this.publishingQos = publishingQos;
        this.payload = payload;
    }

    public Topic getTopic() {
        return topic;
    }

    public MsgQos getPublishingQos() {
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