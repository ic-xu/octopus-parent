package io.octopus.interception.messages;

import io.netty.buffer.ByteBuf;
import io.handler.codec.mqtt.MqttPublishMessage;

public class InterceptPublishMessage extends InterceptAbstractMessage {

    private final MqttPublishMessage msg;
    private final String clientID;
    private final String username;

    public InterceptPublishMessage(MqttPublishMessage msg, String clientID, String username) {
        super(msg);
        this.msg = msg;
        this.clientID = clientID;
        this.username = username;
    }

    public String getTopicName() {
        return msg.variableHeader().topicName();
    }

    public ByteBuf getPayload() {
        return msg.payload();
    }

    public String getClientID() {
        return clientID;
    }

    public String getUsername() {
        return username;
    }

    public MqttPublishMessage getMsg() {
        return msg;
    }
}
