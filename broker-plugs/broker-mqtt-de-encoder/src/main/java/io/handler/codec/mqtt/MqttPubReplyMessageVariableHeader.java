package io.handler.codec.mqtt;

import io.netty.util.internal.StringUtil;

/**
 * Variable Header containing Packet Id, reason code and Properties as in MQTT v5 spec.
 */
public final class MqttPubReplyMessageVariableHeader extends MqttMessageIdVariableHeader {

    private final byte reasonCode;
    private final MqttProperties properties;

    public static final byte REASON_CODE_OK = 0;

    public MqttPubReplyMessageVariableHeader(int messageId, byte reasonCode, MqttProperties properties) {
        super(messageId);
        /// 兼容EMQX 客户端，EMQX 客户端会发送一个packageID=0 的pubAck 包
//        if (messageId < 1 || messageId > 0xffff) {
//            throw new IllegalArgumentException("messageId: " + messageId + " (expected: 1 ~ 65535)");
//        }
        this.reasonCode = reasonCode;
        this.properties = MqttProperties.withEmptyDefaults(properties);
    }

    public byte reasonCode() {
        return reasonCode;
    }

    public MqttProperties properties() {
        return properties;
    }

    @Override
    public String toString() {
        return StringUtil.simpleClassName(this) + "[" +
                "messageId=" + messageId() +
                ", reasonCode=" + reasonCode +
                ", properties=" + properties +
                ']';
    }
}
