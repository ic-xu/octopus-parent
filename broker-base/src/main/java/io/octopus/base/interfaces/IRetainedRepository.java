package io.octopus.base.interfaces;

import io.handler.codec.mqtt.MqttPublishMessage;
import io.octopus.base.subscriptions.RetainedMessage;
import io.octopus.base.subscriptions.Topic;

import java.util.List;

public interface IRetainedRepository {

    void cleanRetained(Topic topic);

    void retain(Topic topic, MqttPublishMessage msg);

    boolean isEmpty();

    List<RetainedMessage> retainedOnTopic(String topic);
}
