package io.octopus.utils;

import io.handler.codec.mqtt.*;
import io.handler.codec.mqtt.utils.MqttDecoderUtils;
import io.handler.codec.mqtt.utils.MqttEncoderUtils;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import java.nio.charset.StandardCharsets;

public class Test {

    public static void main(String[] args) {


        MqttPublishVariableHeader mqttPublishVariableHeader =
                new MqttPublishVariableHeader("testTopic",55);
        MqttFixedHeader mqttFixedHeader = new MqttFixedHeader(MqttMessageType.PUBLISH,true, MqttQoS.AT_LEAST_ONCE,true,0);

        MqttMessage message = MqttMessageFactory.newMessage(mqttFixedHeader, mqttPublishVariableHeader, Unpooled.wrappedBuffer("123456789".getBytes(StandardCharsets.UTF_8)));

        byte[] bytes = MqttEncoderUtils.decodeMessage(message);
        if(null!=bytes){
            System.out.println(bytes.length);
            MqttPublishMessage decode = (MqttPublishMessage) MqttDecoderUtils.decode(bytes);
            String s = decode.variableHeader().topicName();
            System.out.println(s);
            System.out.println(decode.variableHeader().packetId());
            ByteBuf payload = (ByteBuf) decode.payload();
            byte[] array = payload.array();
            String s1 = new String(array, StandardCharsets.UTF_8);
            System.out.println(s1);
        }

    }
}
