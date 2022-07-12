package io.octopus.broker.security;

import io.octopus.kernel.kernel.security.IRWController;
import io.octopus.kernel.kernel.subscriptions.Topic;

/**
 * @author user
 */
public class PermitAllAuthorityController implements IRWController {

    @Override
    public boolean canWrite(Topic topic, String user, String client) {
        return true;
    }

    @Override
    public boolean canRead(Topic topic, String user, String client) {
        return true;
    }

}
