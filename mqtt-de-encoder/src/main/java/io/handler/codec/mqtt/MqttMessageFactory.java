package io.handler.codec.mqtt;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.DecoderResult;

/**
 * Utility class with factory methods to create different types of MQTT messages.
 * @author user
 */
public final class MqttMessageFactory {

    public static MqttMessage newMessage(MqttFixedHeader mqttFixedHeader, Object variableHeader, Object payload) {
        switch (mqttFixedHeader.messageType()) {
            case CUSTOMER:
                return new MqttCustomerMessage(mqttFixedHeader,(MqttCustomerVariableHeader) variableHeader,(ByteBuf)payload,((ByteBuf) payload).readByte());

            case CONNECT :
                return new MqttConnectMessage(
                        mqttFixedHeader,
                        (MqttConnectVariableHeader) variableHeader,
                        (MqttConnectPayload) payload);

            case CONNACK:
                return new MqttConnAckMessage(mqttFixedHeader, (MqttConnAckVariableHeader) variableHeader);

            case SUBSCRIBE:
                return new MqttSubscribeMessage(
                        mqttFixedHeader,
                        (MqttMessageIdVariableHeader) variableHeader,
                        (MqttSubscribePayload) payload);

            case SUBACK:
                return new MqttSubAckMessage(
                        mqttFixedHeader,
                        (MqttMessageIdVariableHeader) variableHeader,
                        (MqttSubAckPayload) payload);

            case UNSUBACK:
                return new MqttUnsubAckMessage(
                        mqttFixedHeader,
                        (MqttMessageIdVariableHeader) variableHeader,
                        (MqttUnsubAckPayload) payload);

            case UNSUBSCRIBE:
                return new MqttUnsubscribeMessage(
                        mqttFixedHeader,
                        (MqttMessageIdVariableHeader) variableHeader,
                        (MqttUnsubscribePayload) payload);

            case PUBLISH:
                return new MqttPublishMessage(
                        mqttFixedHeader,
                        (MqttPublishVariableHeader) variableHeader,
                        (ByteBuf) payload);

            case PUBACK:
                //Having MqttPubReplyMessageVariableHeader or MqttMessageIdVariableHeader
                return new MqttPubAckMessage(mqttFixedHeader, (MqttMessageIdVariableHeader) variableHeader);
            case PUBREC:
                return new MqttPubRecMessage(
                    mqttFixedHeader,
                    (MqttMessageIdVariableHeader)variableHeader);
            case PUBREL:
                return new MqttPubRelMessage(
                    mqttFixedHeader,
                    (MqttMessageIdVariableHeader)variableHeader);
            case PUBCOMP:
                //Having MqttPubReplyMessageVariableHeader or MqttMessageIdVariableHeader
                return new MqttPubCompMessage(mqttFixedHeader, (MqttMessageIdVariableHeader)variableHeader);

            case PINGREQ:
            case PINGRESP:
                return new MqttMessage(mqttFixedHeader);

            case DISCONNECT:
            case AUTH:
                //Having MqttReasonCodeAndPropertiesVariableHeader
                return new MqttMessage(mqttFixedHeader,
                        (MqttReasonCodeAndPropertiesVariableHeader) variableHeader);

            default:
                throw new IllegalArgumentException("unknown message type: " + mqttFixedHeader.messageType());
        }
    }



    public static MqttMessage newInvalidMessage(Throwable cause) {
        return new MqttMessage(0L,null, null, null, DecoderResult.failure(cause));
    }

    public static MqttPubRelMessage newPubRelMessage(int packageId){
        MqttFixedHeader mqttFixedHeader = new MqttFixedHeader(MqttMessageType.PUBREL, false, MqttQoS.AT_LEAST_ONCE, false, 0);
        return new MqttPubRelMessage(mqttFixedHeader, MqttMessageIdVariableHeader.from(packageId));
    }


    public static MqttMessage newInvalidMessage(MqttFixedHeader mqttFixedHeader, Object variableHeader,
                                                Throwable cause) {
        return new MqttMessage(mqttFixedHeader, variableHeader, null, DecoderResult.failure(cause));
    }

    private MqttMessageFactory() { }
}
