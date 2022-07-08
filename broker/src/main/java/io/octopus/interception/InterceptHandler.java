package io.octopus.interception;

import io.handler.codec.mqtt.MqttMessage;
import io.handler.codec.mqtt.MqttPublishMessage;
import io.octopus.interception.messages.*;
import io.octopus.kernel.kernel.subscriptions.Subscription;

/**
 * This interface is used to inject code for intercepting broker events.
 * <p>
 * The events can act only as observers.
 * <p>
 * Almost every method receives a subclass of {@link MqttMessage}, except <code>onDisconnect</code>
 * that receives the client id string and <code>onSubscribe</code> and <code>onUnsubscribe</code>
 * that receive a {@link Subscription} object.
 */
public interface InterceptHandler {

    Class<?>[] ALL_MESSAGE_TYPES = {InterceptConnectMessage.class, InterceptDisconnectMessage.class,
            InterceptConnectionLostMessage.class, InterceptPublishMessage.class, InterceptSubscribeMessage.class,
            InterceptUnsubscribeMessage.class, InterceptAcknowledgedMessage.class};

    /**
     * @return the identifier of this intercept handler.
     */
    String getID();

    /**
     * @return the InterceptMessage subtypes that this handler can process. If the result is null or
     * equal to ALL_MESSAGE_TYPES, all the message types will be processed.
     */
    Class<?>[] getInterceptedMessageTypes();

    void onConnect(InterceptConnectMessage msg);

    void onDisconnect(InterceptDisconnectMessage msg);

    void onConnectionLost(InterceptConnectionLostMessage msg);

    void onBeforePublish(MqttPublishMessage msg);

    void onPublish(InterceptPublishMessage msg);

    void onSubscribe(InterceptSubscribeMessage msg);

    void onUnsubscribe(InterceptUnsubscribeMessage msg);

    void onMessageAcknowledged(InterceptAcknowledgedMessage msg);
}
