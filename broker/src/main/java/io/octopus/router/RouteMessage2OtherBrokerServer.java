package io.octopus.router;

import io.handler.codec.mqtt.MqttMessage;
import io.octopus.udp.message.MessageSendListener;

public interface RouteMessage2OtherBrokerServer {

    void router(MqttMessage mqttMessage, MessageSendListener messageSendListener) ;
}
