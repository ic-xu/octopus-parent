package io.handler.codec.mqtt.utils;

import io.handler.codec.mqtt.*;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.EncoderException;

import java.util.List;

import static io.netty.buffer.ByteBufUtil.*;

public class MessageEncoderUtils {


    public static byte[] decodeMessage(IMessage message) {
        if (message instanceof MqttMessage) {
            final MqttMessage msg = (MqttMessage) message;
            switch (msg.fixedHeader().messageType()) {
                case CUSTOMER:
                    return encodeCustomerMessage((MqttCustomerMessage) message);
                case PUBLISH:
                    return encodePublishMessage((MqttPublishMessage) message);
                default:
                    return null;
            }
        }
        return null;
    }

    private static byte[] encodeCustomerMessage(MqttCustomerMessage message) {
        MqttFixedHeader mqttFixedHeader = message.fixedHeader();
        ByteBuf payload = message.payload().copy();
        int fixedHeaderBufferSize = 3 + getVariableLengthInt(payload.readableBytes() + 1);

        ByteBuf buf = Unpooled.buffer(fixedHeaderBufferSize + payload.readableBytes() + 1);
        try {
            //write MQTT Control Packet type
            buf.writeByte(getFixedHeaderByte1(mqttFixedHeader));
            // write Remaining Length
            writeVariableLengthInt(buf, payload.readableBytes() + 3);
            // write message id
            buf.writeShort(message.variableHeader().getPackageId());
            // write customer message type
            buf.writeByte(message.getMessageType());
            // write payload
            buf.writeBytes(payload);
            return buf.array();
        } finally {
            payload.release();
        }

    }

    private static byte[] encodePublishMessage(MqttPublishMessage message) {
        MqttFixedHeader mqttFixedHeader = message.fixedHeader();
        MqttPublishVariableHeader variableHeader = message.variableHeader();
        ByteBuf payload = message.payload().duplicate();

        String topicName = variableHeader.topicName();
        int topicNameBytes = utf8Bytes(topicName);

        ByteBuf propertiesBuf = encodePropertiesIfNeeded(MqttVersion.MQTT_3_1_1,
                ByteBufAllocator.DEFAULT,
                message.variableHeader().properties());

        try {
            int variableHeaderBufferSize = 2 + topicNameBytes +
                    (mqttFixedHeader.qosLevel().value() > 0 ? 2 : 0) + propertiesBuf.readableBytes();
            int payloadBufferSize = payload.readableBytes();
            int variablePartSize = variableHeaderBufferSize + payloadBufferSize;
            int fixedHeaderBufferSize = 1 + getVariableLengthInt(variablePartSize);

            ByteBuf buf = Unpooled.buffer(fixedHeaderBufferSize + variablePartSize);
            buf.writeByte(getFixedHeaderByte1(mqttFixedHeader));
            writeVariableLengthInt(buf, variablePartSize);
            writeExactUTF8String(buf, topicName, topicNameBytes);
            buf.writeShort(variableHeader.packetId());
            buf.writeBytes(propertiesBuf);
            buf.writeBytes(payload);

            return buf.array();
        } finally {
            propertiesBuf.release();
        }
    }


    private static ByteBuf encodePropertiesIfNeeded(MqttVersion mqttVersion,
                                                    ByteBufAllocator byteBufAllocator,
                                                    MqttProperties mqttProperties) {
        if (mqttVersion == MqttVersion.MQTT_5) {
            return encodeProperties(byteBufAllocator, mqttProperties);
        }
        return Unpooled.EMPTY_BUFFER;
    }

    private static ByteBuf encodeProperties(ByteBufAllocator byteBufAllocator,
                                            MqttProperties mqttProperties) {
        ByteBuf propertiesHeaderBuf = byteBufAllocator.buffer();
        // encode also the Properties part
        try {
            ByteBuf propertiesBuf = byteBufAllocator.buffer();
            try {
                for (MqttProperties.MqttProperty property : mqttProperties.listAll()) {
                    MqttProperties.MqttPropertyType propertyType =
                            MqttProperties.MqttPropertyType.valueOf(property.propertyId());
                    switch (propertyType) {
                        case PAYLOAD_FORMAT_INDICATOR:
                        case REQUEST_PROBLEM_INFORMATION:
                        case REQUEST_RESPONSE_INFORMATION:
                        case MAXIMUM_QOS:
                        case RETAIN_AVAILABLE:
                        case WILDCARD_SUBSCRIPTION_AVAILABLE:
                        case SUBSCRIPTION_IDENTIFIER_AVAILABLE:
                        case SHARED_SUBSCRIPTION_AVAILABLE:
                            writeVariableLengthInt(propertiesBuf, property.propertyId());
                            final byte bytePropValue = ((MqttProperties.IntegerProperty) property).value().byteValue();
                            propertiesBuf.writeByte(bytePropValue);
                            break;
                        case SERVER_KEEP_ALIVE:
                        case RECEIVE_MAXIMUM:
                        case TOPIC_ALIAS_MAXIMUM:
                        case TOPIC_ALIAS:
                            writeVariableLengthInt(propertiesBuf, property.propertyId());
                            final short twoBytesInPropValue =
                                    ((MqttProperties.IntegerProperty) property).value().shortValue();
                            propertiesBuf.writeShort(twoBytesInPropValue);
                            break;
                        case PUBLICATION_EXPIRY_INTERVAL:
                        case SESSION_EXPIRY_INTERVAL:
                        case WILL_DELAY_INTERVAL:
                        case MAXIMUM_PACKET_SIZE:
                            writeVariableLengthInt(propertiesBuf, property.propertyId());
                            final int fourBytesIntPropValue = ((MqttProperties.IntegerProperty) property).value();
                            propertiesBuf.writeInt(fourBytesIntPropValue);
                            break;
                        case SUBSCRIPTION_IDENTIFIER:
                            writeVariableLengthInt(propertiesBuf, property.propertyId());
                            final int vbi = ((MqttProperties.IntegerProperty) property).value();
                            writeVariableLengthInt(propertiesBuf, vbi);
                            break;
                        case CONTENT_TYPE:
                        case RESPONSE_TOPIC:
                        case ASSIGNED_CLIENT_IDENTIFIER:
                        case AUTHENTICATION_METHOD:
                        case RESPONSE_INFORMATION:
                        case SERVER_REFERENCE:
                        case REASON_STRING:
                            writeVariableLengthInt(propertiesBuf, property.propertyId());
                            writeEagerUTF8String(propertiesBuf, ((MqttProperties.StringProperty) property).value());
                            break;
                        case USER_PROPERTY:
                            final List<MqttProperties.StringPair> pairs =
                                    ((MqttProperties.UserProperties) property).value();
                            for (MqttProperties.StringPair pair : pairs) {
                                writeVariableLengthInt(propertiesBuf, property.propertyId());
                                writeEagerUTF8String(propertiesBuf, pair.key);
                                writeEagerUTF8String(propertiesBuf, pair.value);
                            }
                            break;
                        case CORRELATION_DATA:
                        case AUTHENTICATION_DATA:
                            writeVariableLengthInt(propertiesBuf, property.propertyId());
                            final byte[] binaryPropValue = ((MqttProperties.BinaryProperty) property).value();
                            propertiesBuf.writeShort(binaryPropValue.length);
                            propertiesBuf.writeBytes(binaryPropValue, 0, binaryPropValue.length);
                            break;
                        default:
                            //shouldn't reach here
                            throw new EncoderException("Unknown property type: " + propertyType);
                    }
                }
                writeVariableLengthInt(propertiesHeaderBuf, propertiesBuf.readableBytes());
                propertiesHeaderBuf.writeBytes(propertiesBuf);

                return propertiesHeaderBuf;
            } finally {
                propertiesBuf.release();
            }
        } catch (RuntimeException e) {
            propertiesHeaderBuf.release();
            throw e;
        }
    }

    private static int getFixedHeaderByte1(MqttFixedHeader header) {
        int ret = 0;
        ret |= header.messageType().value() << 4;
        if (header.isDup()) {
            ret |= 0x08;
        }
        ret |= header.qosLevel().value() << 1;
        if (header.isRetain()) {
            ret |= 0x01;
        }
        return ret;
    }

    private static void writeVariableLengthInt(ByteBuf buf, int num) {
        do {
            int digit = num % 128;
            num /= 128;
            if (num > 0) {
                digit |= 0x80;
            }
            buf.writeByte(digit);
        } while (num > 0);
    }

    private static int nullableMaxUtf8Bytes(String s) {
        return s == null ? 0 : utf8MaxBytes(s);
    }

    private static void writeExactUTF8String(ByteBuf buf, String s, int utf8Length) {
        buf.ensureWritable(utf8Length + 2);
        buf.writeShort(utf8Length);
        if (utf8Length > 0) {
            final int writtenUtf8Length = reserveAndWriteUtf8(buf, s, utf8Length);
            assert writtenUtf8Length == utf8Length;
        }
    }

    private static void writeEagerUTF8String(ByteBuf buf, String s) {
        final int maxUtf8Length = nullableMaxUtf8Bytes(s);
        buf.ensureWritable(maxUtf8Length + 2);
        final int writerIndex = buf.writerIndex();
        final int startUtf8String = writerIndex + 2;
        buf.writerIndex(startUtf8String);
        final int utf8Length = s != null ? reserveAndWriteUtf8(buf, s, maxUtf8Length) : 0;
        buf.setShort(writerIndex, utf8Length);
    }


    private static int getVariableLengthInt(int num) {
        int count = 0;
        do {
            num /= 128;
            count++;
        } while (num > 0);
        return count;
    }

}
