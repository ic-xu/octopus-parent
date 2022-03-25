package io.handler.codec.mqtt;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufHolder;
import io.netty.buffer.ByteBufUtil;

/**
 * See <a href="https://public.dhe.ibm.com/software/dw/webservices/ws-mqtt/mqtt-v3r1.html#publish">MQTTV3.1/publish</a>
 */
public class MqttPublishMessage extends MqttMessage implements ByteBufHolder {

    public MqttPublishMessage(
            MqttFixedHeader mqttFixedHeader,
            MqttPublishVariableHeader variableHeader,
            ByteBuf payload) {
        super(mqttFixedHeader, variableHeader, payload);
    }

    public MqttPublishMessage(Long messageId,
                              MqttFixedHeader mqttFixedHeader,
                              MqttPublishVariableHeader variableHeader,
                              ByteBuf payload) {
        super(messageId,mqttFixedHeader, variableHeader, payload);
    }

    @Override
    public MqttPublishVariableHeader variableHeader() {
        return (MqttPublishVariableHeader) super.variableHeader();
    }

    @Override
    public ByteBuf payload() {
        return content();
    }

    @Override
    public ByteBuf content() {
        return ByteBufUtil.ensureAccessible((ByteBuf) super.payload());
    }

    @Override
    public MqttPublishMessage copy() {
        return replace(content().copy());
    }

    @Override
    public MqttPublishMessage duplicate() {
        return replace(content().duplicate());
    }

    @Override
    public MqttPublishMessage retainedDuplicate() {
        return replace(content().retainedDuplicate());
    }

    @Override
    public MqttPublishMessage replace(ByteBuf content) {
        MqttPublishMessage mqttPublishMessage = new MqttPublishMessage(fixedHeader(), variableHeader(), content);
        return mqttPublishMessage;
    }

    @Override
    public int refCnt() {
        return content().refCnt();
    }

    @Override
    public MqttPublishMessage retain() {
        content().retain();
        return this;
    }

    @Override
    public MqttPublishMessage retain(int increment) {
        content().retain(increment);
        return this;
    }

    @Override
    public MqttPublishMessage touch() {
        content().touch();
        return this;
    }

    @Override
    public MqttPublishMessage touch(Object hint) {
        content().touch(hint);
        return this;
    }

    @Override
    public boolean release() {
        return content().release();
    }

    @Override
    public boolean release(int decrement) {
        return content().release(decrement);
    }

}
