package io.octopus.utils;

import io.handler.codec.mqtt.*;
import io.netty.buffer.ByteBuf;

public class MqttMessageUtils {

   public static MqttPublishMessage notRetainedPublish(String topic, MqttQoS qos, ByteBuf message) {
        MqttFixedHeader fixedHeader = new MqttFixedHeader(MqttMessageType.PUBLISH, false, qos, false, 0);
        MqttPublishVariableHeader varHeader = new MqttPublishVariableHeader(topic, 0);
        return new MqttPublishMessage(fixedHeader, varHeader, message);
    }



//    public static Message notRetainedPublish(String topic, MessageQos qos, ByteBuf message) {
//        MqttFixedHeader fixedHeader = new MqttFixedHeader(MqttMessageType.PUBLISH, false, qos, false, 0);
//        MqttPublishVariableHeader varHeader = new MqttPublishVariableHeader(topic, 0);
//        return new Message(fixedHeader, varHeader, message);
//    }
}
