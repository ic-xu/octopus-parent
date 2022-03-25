package io.octopus.interception.messages;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.handler.codec.mqtt.MqttQoS;
import java.io.Serializable;

public class InterceptAcknowledgedMessage implements InterceptMessage {

    class StoredMessage implements Serializable {

        private static final long serialVersionUID = 1755296138639817304L;
        private MqttQoS mqttQoS;
        final byte[] mPayload;
        final String mTopic;
        private boolean mRetained;
        private String mClientId;

        public StoredMessage(byte[] message, MqttQoS qos, String topic) {
            mqttQoS = qos;
            mPayload = message;
            mTopic = topic;
        }

        public void setQos(MqttQoS qos) {
            this.mqttQoS = qos;
        }

        public MqttQoS getQos() {
            return mqttQoS;
        }

        public String getTopic() {
            return mTopic;
        }

        public String getClientID() {
            return mClientId;
        }

        public void setClientID(String mClientId) {
            this.mClientId = mClientId;
        }

        public ByteBuf getPayload() {
            return Unpooled.copiedBuffer(mPayload);
        }

        public void setRetained(boolean retained) {
            this.mRetained = retained;
        }

        public boolean isRetained() {
            return mRetained;
        }

        @Override
        public String toString() {
            return "PublishEvent{clientID='" + mClientId + '\'' + ", m_retain="
                + mRetained + ", m_qos=" + mqttQoS + ", m_topic='" + mTopic + '\'' + '}';
        }
    }

    private final StoredMessage msg;
    private final String username;
    private final String topic;
    private final int packetID;

    public InterceptAcknowledgedMessage(StoredMessage msg, String topic, String username, int packetID) {
        this.msg = msg;
        this.username = username;
        this.topic = topic;
        this.packetID = packetID;
    }

    public StoredMessage getMsg() {
        return msg;
    }

    public String getUsername() {
        return username;
    }

    public String getTopic() {
        return topic;
    }

    public int getPacketID() {
        return packetID;
    }
}
