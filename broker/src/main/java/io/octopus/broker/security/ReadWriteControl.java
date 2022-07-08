package io.octopus.broker.security;

import io.handler.codec.mqtt.MqttQoS;
import io.handler.codec.mqtt.MqttSubscribeMessage;
import io.handler.codec.mqtt.MqttTopicSubscription;
import io.octopus.kernel.kernel.message.MsgQos;
import io.octopus.kernel.kernel.security.IRWController;
import io.octopus.kernel.kernel.subscriptions.Subscription;
import io.octopus.kernel.kernel.subscriptions.Topic;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import static io.handler.codec.mqtt.MqttQoS.FAILURE;
import static io.octopus.utils.Utils.messageId;

/**
 * @author user
 */
public final class ReadWriteControl implements IRWController{

    private static final Logger LOGGER = LoggerFactory.getLogger(ReadWriteControl.class);

    private final IRWController policy;

    public ReadWriteControl(IRWController policy) {
        this.policy = policy;
    }

    /**
     * @param clientID the clientID
     * @param username the username
     * @param msg      the subscribe message to verify
     * @return the list of verified topics for the given subscribe message.
     */
    public List<MqttTopicSubscription> verifyTopicsReadAccess(String clientID, String username, MqttSubscribeMessage msg) {
        List<MqttTopicSubscription> ackTopics = new ArrayList<>();

        final int messageId = messageId(msg);
        for (MqttTopicSubscription req : msg.payload().topicSubscriptions()) {
            Topic topic = new Topic(req.topicName());
            if (!policy.canRead(topic, username, clientID)) {
                // send SUBACK with 0x80, the user hasn't credentials to read the topic
                LOGGER.warn("Client does not have read permissions on the topic CId={}, username: {}, messageId: {}, " +
                        "topic: {}", clientID, username, messageId, topic);
                ackTopics.add(new MqttTopicSubscription(topic.toString(), FAILURE));
            } else {
                MqttQoS qos;
                if (topic.isValid()) {
                    LOGGER.debug("Client will be subscribed to the topic CId={}, username: {}, messageId: {}, topic: {}",
                            clientID, username, messageId, topic);
                    qos = req.qualityOfService();
                } else {
                    LOGGER.warn("Topic filter is not valid CId={}, username: {}, messageId: {}, topic: {}", clientID,
                            username, messageId, topic);
                    qos = FAILURE;
                }
                ackTopics.add(new MqttTopicSubscription(topic.toString(), qos));
            }
        }
        return ackTopics;
    }



    /**
     * @param clientID the clientID
     * @param username the username
     * @param subscriptions      the subscribe message to verify
     * @return the list of verified topics for the given subscribe message.
     */
    public List<Subscription> verifyTopicsReadAccess(String clientID, String username, List<Subscription> subscriptions) {
        for (Subscription sub:subscriptions) {
            if (!policy.canRead(sub.topicFilter, username, clientID)) {
                // send SUBACK with 0x80, the user hasn't credentials to read the topic
                LOGGER.warn("Client does not have read permissions on the topic CId={}, username: {}, " +
                        "topic: {}", clientID, username, sub.topicFilter);
                sub.setRequestedQos(MsgQos.FAILURE);
            } else {
                if (sub.topicFilter.isValid()) {
                    LOGGER.debug("Client will be subscribed to the topic CId={}, username: {}, topic: {}",
                            clientID, username, sub.topicFilter);
                } else {
                    LOGGER.warn("Topic filter is not valid CId={}, username: {}, topic: {}", clientID,
                            username, sub.topicFilter);
                    sub.setRequestedQos(MsgQos.FAILURE);
                }
            }
        }
        return subscriptions;


//        List<MqttTopicSubscription> ackTopics = new ArrayList<>();
//
//        final int messageId = messageId(msg);
//        for (MqttTopicSubscription req : msg.payload().topicSubscriptions()) {
//            Topic topic = new Topic(req.topicName());
//            if (!policy.canRead(topic, username, clientID)) {
//                // send SUBACK with 0x80, the user hasn't credentials to read the topic
//                LOGGER.warn("Client does not have read permissions on the topic CId={}, username: {}, messageId: {}, " +
//                        "topic: {}", clientID, username, messageId, topic);
//                ackTopics.add(new MqttTopicSubscription(topic.toString(), FAILURE));
//            } else {
//                MqttQoS qos;
//                if (topic.isValid()) {
//                    LOGGER.debug("Client will be subscribed to the topic CId={}, username: {}, messageId: {}, topic: {}",
//                            clientID, username, messageId, topic);
//                    qos = req.qualityOfService();
//                } else {
//                    LOGGER.warn("Topic filter is not valid CId={}, username: {}, messageId: {}, topic: {}", clientID,
//                            username, messageId, topic);
//                    qos = FAILURE;
//                }
//                ackTopics.add(new MqttTopicSubscription(topic.toString(), qos));
//            }
//        }
//        return ackTopics;
    }

    /**
     * Ask the authorization policy if the topic can be used in a publish.
     *
     * @param topic  the topic to write to.
     * @param user   the user
     * @param client the client
     * @return true if the user from client can publish data on topic.
     */
    @Override
    public boolean canWrite(Topic topic, String user, String client) {
        return policy.canWrite(topic, user, client);
    }

    @Override
    public boolean canRead(Topic topic, String user, String client) {
        return policy.canRead(topic, user, client);
    }
}
