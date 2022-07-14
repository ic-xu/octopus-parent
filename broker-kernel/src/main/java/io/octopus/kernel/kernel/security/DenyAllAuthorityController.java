package io.octopus.kernel.kernel.security;

import io.octopus.kernel.kernel.security.IRWController;
import io.octopus.kernel.kernel.subscriptions.Topic;

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
