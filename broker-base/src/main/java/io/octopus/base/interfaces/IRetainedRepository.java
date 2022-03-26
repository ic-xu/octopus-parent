package io.octopus.base.interfaces;

import io.handler.codec.mqtt.MqttPublishMessage;
import io.octopus.base.subscriptions.RetainedMessage;
import io.octopus.base.subscriptions.Topic;

import java.util.List;

/**
 *
 * retained Msg
 * @author user
 */
public interface IRetainedRepository {

    /**
     * clean the retain msg
     * @param topic topic of retain
     */
    void cleanRetained(Topic topic);

    /**
     * sava retain msg
     * @param topic topic of msg
     * @param msg message
     */
    void retain(Topic topic, MqttPublishMessage msg);

    /**
     * has retain message
     * @return boolean
     */
    boolean isEmpty();

    /**
     * retri msg
     * @param topic
     * @return
     */
    List<RetainedMessage> retainedOnTopic(String topic);
}
