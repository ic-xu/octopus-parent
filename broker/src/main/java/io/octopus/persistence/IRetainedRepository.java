package io.octopus.persistence;

import io.octopus.broker.RetainedMessage;
import io.octopus.broker.subscriptions.Topic;
import io.handler.codec.mqtt.MqttPublishMessage;

import java.util.List;

public interface IRetainedRepository {

    void cleanRetained(Topic topic);

    void retain(Topic topic, MqttPublishMessage msg);

    boolean isEmpty();

    List<RetainedMessage> retainedOnTopic(String topic);
}
