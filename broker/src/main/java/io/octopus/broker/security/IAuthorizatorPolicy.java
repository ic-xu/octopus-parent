package io.octopus.broker.security;

import io.octopus.broker.subscriptions.Topic;

/**
 * ACL checker.
 *
 * Create an authorizator that matches topic names with same grammar of subscriptions. The # is
 * always a terminator and its the multilevel matcher. The + sign is the single level matcher.
 */
public interface IAuthorizatorPolicy {

    /**
     * Ask the implementation of the authorizator if the topic can be used in a publish.
     *
     * @param topic
     *            the topic to write to.
     * @param user
     *            the user
     * @param client
     *            the client
     * @return true if the user from client can publish data on topic.
     */
    boolean canWrite(Topic topic, String user, String client);

    boolean canRead(Topic topic, String user, String client);
}
