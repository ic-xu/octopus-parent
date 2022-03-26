package io.octopus.base.interfaces;

import io.octopus.base.subscriptions.Topic;

/**
 * ACL checker.
 * <p>
 * Create an Authority controller that matches topic names with same grammar of subscriptions. The # is
 * always a terminator and its the multilevel matcher. The + sign is the single level matcher.
 */
public interface IRWController {

    /**
     * Ask the implementation of the authorizator if the topic can be used in a publish.
     *
     * @param topic  the topic to write to.
     * @param user   the user
     * @param client the client
     * @return true if the user from client can publish data on topic.
     */
    boolean canWrite(Topic topic, String user, String client);


    /**
     *
     * @param topic
     * @param user
     * @param client
     * @return
     */
    boolean canRead(Topic topic, String user, String client);
}
