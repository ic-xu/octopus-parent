package io.octopus.broker.security;

import io.octopus.broker.subscriptions.Topic;

public class DenyAllAuthorizatorPolicy implements IAuthorizatorPolicy {

    @Override
    public boolean canRead(Topic topic, String user, String client) {
        return false;
    }

    @Override
    public boolean canWrite(Topic topic, String user, String client) {
        return false;
    }
}
