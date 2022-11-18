package io.octopus.kernel.kernel.subscriptions;

import io.octopus.kernel.kernel.message.MsgQos;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Objects;
import java.util.Properties;

/**
 * @author user
 */
public class RetainedMessage implements Serializable{

    private final Topic topic;
    private final MsgQos qos;
    private final byte[] payload;
    private final Properties properties;
    public RetainedMessage(Topic topic, MsgQos qos, byte[] payload, Properties properties) {
        this.topic = topic;
        this.qos = qos;
        this.payload = payload;
        this.properties = properties;
    }


    public Topic getTopic() {
        return topic;
    }

    public MsgQos qosLevel() {
        return qos;
    }

    public byte[] getPayload() {
        return payload;
    }


    public Properties getProperties() {
        return properties;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RetainedMessage)) return false;
        RetainedMessage that = (RetainedMessage) o;
        return topic.equals(that.topic) && qos == that.qos && Arrays.equals(payload, that.payload) && properties.equals(that.getProperties());
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(topic, qos);
        result = 31 * result + Arrays.hashCode(payload);
        return result;
    }

}
