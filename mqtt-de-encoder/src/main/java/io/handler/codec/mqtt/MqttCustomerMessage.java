package io.handler.codec.mqtt;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.util.internal.StringUtil;

/**
 * 1byte (MQTT Control Packet type)
 * 1 -> 4 byte (Remaining Length)
 * 2byte (packageId)
 * 1byte (customer message type,)
 * payload (message body)
 *
 * @author user
 */
public class MqttCustomerMessage extends MqttMessage {

    private byte messageType;

    public MqttCustomerMessage(
            MqttFixedHeader mqttFixedHeader, MqttCustomerVariableHeader variableHeader,
            ByteBuf payload, byte messageType) {
        super(mqttFixedHeader, variableHeader, payload);
        this.messageType = messageType;
    }

    public MqttCustomerMessage(Long messageId,
                               MqttFixedHeader mqttFixedHeader, MqttCustomerVariableHeader variableHeader,
                               ByteBuf payload, byte messageType) {
        super(messageId, mqttFixedHeader, variableHeader, payload);
        this.messageType = messageType;
    }

    public byte getMessageType() {
        return messageType;
    }

    @Override
    public MqttCustomerVariableHeader variableHeader() {
        return (MqttCustomerVariableHeader) super.variableHeader();
    }

    @Override
    public ByteBuf payload() {
        return ByteBufUtil.ensureAccessible((ByteBuf) super.payload());
    }

    @Override
    public String toString() {
        return new StringBuilder(StringUtil.simpleClassName(this))
                .append('[')
                .append("fixedHeader=").append(fixedHeader() != null ? fixedHeader().toString() : "")
                .append(", variableHeader=").append(variableHeader() != null ? variableHeader().toString() : "")
                .append(']')
                .toString();
    }
}
