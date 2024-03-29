package io.handler.codec.mqtt;

/**
 * See <a href="https://public.dhe.ibm.com/software/dw/webservices/ws-mqtt/mqtt-v3r1.html#puback">MQTTV3.1/puback</a>
 */
public final class MqttPubRecMessage extends MqttMessage {

    public MqttPubRecMessage(MqttFixedHeader mqttFixedHeader, MqttMessageIdVariableHeader variableHeader) {
        super(mqttFixedHeader, variableHeader);
    }

    @Override
    public MqttMessageIdVariableHeader variableHeader() {
        return (MqttMessageIdVariableHeader) super.variableHeader();
    }
}
