package io.octopus.interception.messages;
import io.handler.codec.mqtt.MqttQoS;
import io.octopus.base.subscriptions.Subscription;

public class InterceptSubscribeMessage implements InterceptMessage {

    private final Subscription subscription;
    private final String username;

    public InterceptSubscribeMessage(Subscription subscription, String username) {
        this.subscription = subscription;
        this.username = username;
    }

    public String getClientID() {
        return subscription.getClientId();
    }

    public MqttQoS getRequestedQos() {
        return subscription.getRequestedQos();
    }

    public String getTopicFilter() {
        return subscription.getTopicFilter().toString();
    }

    public String getUsername() {
        return username;
    }
}
