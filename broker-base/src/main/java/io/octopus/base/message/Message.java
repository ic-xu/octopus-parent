package io.octopus.base.message;

import io.netty.buffer.ByteBuf;

import java.util.Objects;
import java.util.Properties;

/**
 * @author chenxu
 * @version 1
 * @date 2022/1/5 8:14 下午
 */
public class Message {

    private Long messageId;

    private MessageQos qos;

    private MessageType messageType;

    private String topic;

    private ByteBuf payload;

    public MessageQos getQos() {
        return qos;
    }

    public void setQos(MessageQos qos) {
        this.qos = qos;
    }

    public MessageType getMessageType() {
        return messageType;
    }

    public void setMessageType(MessageType messageType) {
        this.messageType = messageType;
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public ByteBuf getPayload() {
        return payload;
    }

    public void setPayload(ByteBuf payload) {
        this.payload = payload;
    }

    public Long getMessageId() {
        return messageId;
    }

    public void setMessageId(Long messageId) {
        this.messageId = messageId;
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Message)) return false;
        Message message = (Message) o;
        return Objects.equals(getMessageId(), message.getMessageId());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getMessageId(), getQos(), getMessageType(), getTopic(), getPayload());
    }
}
