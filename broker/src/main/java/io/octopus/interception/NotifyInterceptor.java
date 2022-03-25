package io.octopus.interception;

import io.handler.codec.mqtt.MqttConnectMessage;
import io.handler.codec.mqtt.MqttCustomerMessage;
import io.handler.codec.mqtt.MqttPublishMessage;
import io.octopus.broker.subscriptions.Subscription;
import io.octopus.interception.messages.InterceptAcknowledgedMessage;

/**
 * This interface is to be used internally by the broker components.
 * <p>
 * An interface is used instead of a class to allow more flexibility in changing an implementation.
 * <p>
 * plugin implementations forward notifications to a <code>InterceptHandler</code>, that is
 * normally a field. So, the implementations should act as a proxy to a custom intercept handler.
 *
 * @see InterceptHandler
 */
public interface NotifyInterceptor {

    void notifyCustomer(MqttCustomerMessage msg);

    void notifyClientConnected(MqttConnectMessage msg);

    void notifyClientDisconnected(String clientID, String username);

    void notifyClientConnectionLost(String clientID, String username);

    void notifyTopicBeforePublished(MqttPublishMessage msg);

    void notifyTopicPublished(MqttPublishMessage msg, String clientID, String username);

    void notifyTopicSubscribed(Subscription sub, String username);

    void notifyTopicUnsubscribed(String topic, String clientID, String username);

    void notifyMessageAcknowledged(InterceptAcknowledgedMessage msg);

    void addInterceptHandler(InterceptHandler interceptHandler);

    void removeInterceptHandler(InterceptHandler interceptHandler);
}
