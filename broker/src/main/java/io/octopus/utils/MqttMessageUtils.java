package io.octopus.utils;

import io.handler.codec.mqtt.*;
import io.netty.buffer.ByteBuf;

import java.io.IOException;

public class MqttMessageUtils {

   public static MqttPublishMessage notRetainedPublish(String topic, MqttQoS qos, ByteBuf message) {
        MqttFixedHeader fixedHeader = new MqttFixedHeader(MqttMessageType.PUBLISH, false, qos, false, 0);
        MqttPublishVariableHeader varHeader = new MqttPublishVariableHeader(topic, 0);
        return new MqttPublishMessage(fixedHeader, varHeader, message);
    }



    /**
     * Validate that the provided message is an MqttMessage and that it does not contain a failed result.
     *
     * @param message to be validated
     * @return the casted provided message
     * @throws IOException in case of an fail message this will wrap the root cause
     * @throws ClassCastException if the provided message is no MqttMessage
     */
    public static MqttMessage validateMessage(Object message) throws IOException, ClassCastException {
        MqttMessage msg = (MqttMessage) message;
        if (msg.decoderResult() != null && msg.decoderResult().isFailure()) {
            throw new IOException("invalid massage", msg.decoderResult().cause());
        }
        if (msg.fixedHeader() == null) {
            throw new IOException("Unknown packet, no fixedHeader present, no cause provided");
        }
        return msg;
    }


//    public static Message notRetainedPublish(String topic, MessageQos qos, ByteBuf message) {
//        MqttFixedHeader fixedHeader = new MqttFixedHeader(MqttMessageType.PUBLISH, false, qos, false, 0);
//        MqttPublishVariableHeader varHeader = new MqttPublishVariableHeader(topic, 0);
//        return new Message(fixedHeader, varHeader, message);
//    }
}
