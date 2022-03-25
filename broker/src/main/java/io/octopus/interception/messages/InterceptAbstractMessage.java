package io.octopus.interception.messages;

import io.handler.codec.mqtt.MqttMessage;
import io.handler.codec.mqtt.MqttQoS;

public abstract class InterceptAbstractMessage implements InterceptMessage {

    private final MqttMessage msg;

    InterceptAbstractMessage(MqttMessage msg) {
        this.msg = msg;
    }

    public boolean isRetainFlag() {
        return msg.fixedHeader().isRetain();
    }

    public boolean isDupFlag() {
        return msg.fixedHeader().isDup();
    }

    public MqttQoS getQos() {
        return msg.fixedHeader().qosLevel();
    }
}
