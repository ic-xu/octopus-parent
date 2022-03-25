package io.octopus.broker.security;
import io.octopus.base.interfaces.IRWController;
import io.octopus.base.subscriptions.Topic;

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
