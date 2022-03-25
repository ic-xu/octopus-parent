package io.octopus.broker;

import io.octopus.broker.subscriptions.Topic;
import io.handler.codec.mqtt.MqttQoS;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Objects;

public class RetainedMessage implements Serializable{

    private final Topic topic;
    private final MqttQoS qos;
    private final byte[] payload;

    public RetainedMessage(Topic topic, MqttQoS qos, byte[] payload) {
        this.topic = topic;
        this.qos = qos;
        this.payload = payload;
    }

    public Topic getTopic() {
        return topic;
    }

    public MqttQoS qosLevel() {
        return qos;
    }

    public byte[] getPayload() {
        return payload;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RetainedMessage)) return false;
        RetainedMessage that = (RetainedMessage) o;
        return topic.equals(that.topic) && qos == that.qos && Arrays.equals(payload, that.payload);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(topic, qos);
        result = 31 * result + Arrays.hashCode(payload);
        return result;
    }

}
