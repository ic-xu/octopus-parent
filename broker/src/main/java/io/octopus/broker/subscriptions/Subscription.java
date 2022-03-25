package io.octopus.broker.subscriptions;

import io.handler.codec.mqtt.MqttQoS;

import java.io.Serializable;


/**
 * Maintain the information about which Topic a certain ClientID is subscribed and at which QoS
 */
public final class Subscription implements Serializable, Comparable<Subscription>{

    private static final long serialVersionUID = -3383457629635732794L;
    private final MqttQoS requestedQos; // max QoS acceptable
    final String clientId;
    public final Topic topicFilter;

    public Subscription(String clientId, Topic topicFilter, MqttQoS requestedQos) {
        this.requestedQos = requestedQos;
        this.clientId = clientId;
        this.topicFilter = topicFilter;
    }

    public Subscription(Subscription orig) {
        this.requestedQos = orig.requestedQos;
        this.clientId = orig.clientId;
        this.topicFilter = orig.topicFilter;
    }

    public String getClientId() {
        return clientId;
    }

    public MqttQoS getRequestedQos() {
        return requestedQos;
    }

    public Topic getTopicFilter() {
        return topicFilter;
    }

    public boolean qosLessThan(Subscription sub) {
        return requestedQos.value() < sub.requestedQos.value();
    }




    @Override
    public boolean equals(Object o) {
        Subscription that = (Subscription) o;
        try {
            return this.clientId.equals(that.clientId) && this.topicFilter.equals(that.topicFilter);
        } catch (Exception ignore) {
            return false;
        }
    }


    @Override
    public int hashCode() {
        int result1 = clientId != null ? clientId.hashCode() : Integer.MAX_VALUE;
        int result2 = topicFilter != null ? topicFilter.hashCode() : Integer.MAX_VALUE;
        return result1 & result2;
    }

    @Override
    public String toString() {
        return String.format("[filter:%s, clientID: %s, qos: %s]", topicFilter, clientId, requestedQos);
    }

    @Override
    public Subscription clone() {
        try {
            return (Subscription) super.clone();
        } catch (CloneNotSupportedException e) {
            return null;
        }
    }

    @Override
    public int compareTo(Subscription o) {
        int compare = this.clientId.compareTo(o.clientId);
        if (compare != 0) {
            return compare;
        }
        return this.topicFilter.compareTo(o.topicFilter);
    }
}
