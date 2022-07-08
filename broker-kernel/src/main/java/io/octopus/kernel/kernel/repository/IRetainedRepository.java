package io.octopus.kernel.kernel.repository;

import io.octopus.kernel.kernel.message.KernelMsg;
import io.octopus.kernel.kernel.subscriptions.RetainedMessage;
import io.octopus.kernel.kernel.subscriptions.Topic;

import java.util.List;

/**
 * @author user
 */
public interface IRetainedRepository {

    /**
     *  clean all retain msg
     * @param topic topic
     */
    void cleanRetained(Topic topic);

    /**
     * save retain msg
     * @param topic topic
     * @param msg msg
     * @return result
     */
    boolean retain(Topic topic, KernelMsg msg);

    /**
     * empt
     * @return boolean
     */
    boolean isEmpty();

    /**
     * retri
     * @param topic topic
     * @return List<RetainedMessage>
     */
    List<RetainedMessage> retainedOnTopic(String topic);
}
