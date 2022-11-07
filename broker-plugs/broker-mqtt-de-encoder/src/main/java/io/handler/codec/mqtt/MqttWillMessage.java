package io.handler.codec.mqtt;

import io.netty.buffer.ByteBuf;

public class MqttWillMessage {

        final String topic;
        final ByteBuf payload;
        final MqttQoS qos;
        final Boolean retained;

    public MqttWillMessage(String topic, ByteBuf payload, MqttQoS qos, boolean retained) {
            this.topic = topic;
            this.payload = payload;
            this.qos = qos;
            this.retained = retained;
        }


    public String getTopic() {
        return topic;
    }

    public MqttQoS getQos() {
        return qos;
    }

    public ByteBuf getPayload() {
        return payload;
    }

    public Boolean getRetained() {
        return retained;
    }
}
