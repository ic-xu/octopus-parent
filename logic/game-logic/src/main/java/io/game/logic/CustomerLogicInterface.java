package io.game.logic;

import io.handler.codec.mqtt.MqttCustomerMessage;

public interface CustomerLogicInterface {

    void handlerMessage(Object connect, MqttCustomerMessage message);

}
