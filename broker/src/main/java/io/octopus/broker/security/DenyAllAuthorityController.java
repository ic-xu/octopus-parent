package io.octopus.broker.security;

import io.octopus.base.interfaces.IRWController;
import io.octopus.base.subscriptions.Topic;

/**
 * @author user
 */
public class DenyAllAuthorityController implements IRWController {

    @Override
    public boolean canRead(Topic topic, String user, String client) {
        return false;
    }

    @Override
    public boolean canWrite(Topic topic, String user, String client) {
        return false;
    }
}
