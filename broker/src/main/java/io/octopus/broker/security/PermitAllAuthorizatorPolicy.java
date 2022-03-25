package io.octopus.broker.security;

import io.octopus.broker.subscriptions.Topic;

public class PermitAllAuthorizatorPolicy implements IAuthorizatorPolicy {

    @Override
    public boolean canWrite(Topic topic, String user, String client) {
        return true;
    }

    @Override
    public boolean canRead(Topic topic, String user, String client) {
        return true;
    }
}
