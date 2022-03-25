package io.handler.codec.mqtt.utils;


import io.handler.codec.mqtt.*;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import io.netty.util.CharsetUtil;


public class MqttDecoderUtils {
    private static final char[] TOPIC_WILDCARDS = {'#', '+'};

    public static MqttMessage decode(byte[] arr) {
        ByteBuf buf = Unpooled.copiedBuffer(arr);
//        short b1 = buf.readUnsignedByte();
//        MqttMessageType messageType = MqttMessageType.valueOf(b1 >> 4);
        MqttFixedHeader mqttFixedHeader = decodeFixedHeader(buf);
        MqttMessage message = null;
        switch (mqttFixedHeader.messageType()){
            //自定义消息
            case CUSTOMER:
                MqttCustomerVariableHeader mqttCustomerVariableHeader = decodeCustomerVariableHeader(buf);
                byte customerMessageType = buf.readByte();
                message = new MqttCustomerMessage(mqttFixedHeader,mqttCustomerVariableHeader,buf.copy(),customerMessageType);
                break;
                //发布消息
            case PUBLISH:
                //可变头
                MqttPublishVariableHeader mqttPublishVariableHeader = decodePublishVariableHeader(buf, mqttFixedHeader);
                //主题名长度
                message = new MqttPublishMessage(mqttFixedHeader,mqttPublishVariableHeader,buf.copy());
                break;
            default:
                break;
        }
        return message;
    }

    public static MqttMessage decode(long messageId,byte[] arr) {
        ByteBuf buf = Unpooled.copiedBuffer(arr);
//        short b1 = buf.readUnsignedByte();
//        MqttMessageType messageType = MqttMessageType.valueOf(b1 >> 4);
        MqttFixedHeader mqttFixedHeader = decodeFixedHeader(buf);
        MqttMessage message = null;
        switch (mqttFixedHeader.messageType()){
            //自定义消息
            case CUSTOMER:
                MqttCustomerVariableHeader mqttCustomerVariableHeader = decodeCustomerVariableHeader(buf);
                byte customerMessageType = buf.readByte();
                message = new MqttCustomerMessage(messageId,mqttFixedHeader,mqttCustomerVariableHeader,buf.copy(),customerMessageType);
                break;
            //发布消息
            case PUBLISH:
                //可变头
                MqttPublishVariableHeader mqttPublishVariableHeader = decodePublishVariableHeader(buf, mqttFixedHeader);
                //主题名长度
                message = new MqttPublishMessage(messageId,mqttFixedHeader,mqttPublishVariableHeader,buf.copy());
                break;
            default:
                break;
        }
        return message;
    }

    /**
     * 自定义消息可变头
     * @param buffer
     * @return
     */
    private static MqttCustomerVariableHeader decodeCustomerVariableHeader(ByteBuf buffer) {
        return new MqttCustomerVariableHeader(buffer.readShort());
    }


    /**
     * 解码 publish 可变头
     * @param buffer
     * @param mqttFixedHeader
     * @return
     */
    private static MqttPublishVariableHeader decodePublishVariableHeader(
            ByteBuf buffer,
            MqttFixedHeader mqttFixedHeader) {
        final String decodedTopic = decodeString(buffer);
        if (!isValidPublishTopicName(decodedTopic)) {
            throw new DecoderException("invalid publish topic name: " + decodedTopic + " (contains wildcards)");
        }

        int messageId = -1;
        if (mqttFixedHeader.qosLevel().value() > 0) {
            messageId = decodeMessageId(buffer);
        }

        final MqttProperties properties=MqttProperties.NO_PROPERTIES;
        return new MqttPublishVariableHeader(decodedTopic, messageId, properties);
    }


    /**
     * @return messageId with numberOfBytesConsumed is 2
     */
    private static int decodeMessageId(ByteBuf buffer) {
        final int messageId = decodeMsbLsb(buffer);
        if (messageId == 0) {
            throw new DecoderException("invalid messageId: " + messageId);
        }
        return messageId;
    }

    static boolean isValidPublishTopicName(String topicName) {
        // publish topic name must not contain any wildcard
        for (char c : TOPIC_WILDCARDS) {
            if (topicName.indexOf(c) >= 0) {
                return false;
            }
        }
        return true;
    }

    private static String decodeString(ByteBuf buffer) {
        int size = decodeMsbLsb(buffer);
        String s = buffer.toString(buffer.readerIndex(), size, CharsetUtil.UTF_8);
        buffer.skipBytes(size);
        return s;
    }

    /**
     * numberOfBytesConsumed = 2. return decoded result.
     */
    private static int decodeMsbLsb(ByteBuf buffer) {
        int min = 0;
        int max = 65535;
        short msbSize = buffer.readUnsignedByte();
        short lsbSize = buffer.readUnsignedByte();
        int result = msbSize << 8 | lsbSize;
        if (result < min || result > max) {
            result = -1;
        }
        return result;
    }

    /**
     * Decodes the fixed header. It's one byte for the flags and then variable bytes for the remaining length.
     *
     * @param buffer the buffer to decode from
     * @return the fixed header
     */
    private static MqttFixedHeader decodeFixedHeader(ByteBuf buffer) {
        short b1 = buffer.readUnsignedByte();

        MqttMessageType messageType = MqttMessageType.valueOf(b1 >> 4);
        boolean dupFlag = (b1 & 0x08) == 0x08;
        int qosLevel = (b1 & 0x06) >> 1;
        boolean retain = (b1 & 0x01) != 0;

        int remainingLength = 0;
        int multiplier = 1;
        short digit;
        int loops = 0;
        do {
            digit = buffer.readUnsignedByte();
            remainingLength += (digit & 127) * multiplier;
            multiplier *= 128;
            loops++;
        } while ((digit & 128) != 0 && loops < 4);

        // MQTT protocol limits Remaining Length to 4 bytes
        if (loops == 4 && (digit & 128) != 0) {
            throw new DecoderException("remaining length exceeds 4 digits (" + messageType + ')');
        }
        MqttFixedHeader decodedFixedHeader =
                new MqttFixedHeader(messageType, dupFlag, MqttQoS.valueOf(qosLevel), retain, remainingLength);
        return validateFixedHeader(resetUnusedFields(decodedFixedHeader));
    }

    static MqttFixedHeader validateFixedHeader( MqttFixedHeader mqttFixedHeader) {
        switch (mqttFixedHeader.messageType()) {
            case UNSUBSCRIBE:
                if (mqttFixedHeader.qosLevel() != MqttQoS.AT_LEAST_ONCE) {
                    throw new DecoderException(mqttFixedHeader.messageType().name() + " message must have QoS 1");
                }
                return mqttFixedHeader;
            case AUTH:
//                if (MqttCodecUtil.getMqttVersion(ctx) != MqttVersion.MQTT_5) {
//                    throw new DecoderException("AUTH message requires at least MQTT 5");
//                }
                return mqttFixedHeader;
            default:
                return mqttFixedHeader;
        }
    }

    static MqttFixedHeader resetUnusedFields(MqttFixedHeader mqttFixedHeader) {
        switch (mqttFixedHeader.messageType()) {
            case CONNECT:
            case CONNACK:
            case PUBACK:
            case PUBREC:
            case PUBCOMP:
            case SUBACK:
            case UNSUBACK:
            case PINGREQ:
            case PINGRESP:
            case DISCONNECT:
                if (mqttFixedHeader.isDup() ||
                        mqttFixedHeader.qosLevel() != MqttQoS.AT_MOST_ONCE ||
                        mqttFixedHeader.isRetain()) {
                    return new MqttFixedHeader(
                            mqttFixedHeader.messageType(),
                            false,
                            MqttQoS.AT_MOST_ONCE,
                            false,
                            mqttFixedHeader.remainingLength());
                }
                return mqttFixedHeader;
            case PUBREL:
            case SUBSCRIBE:
            case UNSUBSCRIBE:
                if (mqttFixedHeader.isRetain()) {
                    return new MqttFixedHeader(
                            mqttFixedHeader.messageType(),
                            mqttFixedHeader.isDup(),
                            mqttFixedHeader.qosLevel(),
                            false,
                            mqttFixedHeader.remainingLength());
                }
                return mqttFixedHeader;
            default:
                return mqttFixedHeader;
        }
    }

}
