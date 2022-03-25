package io.octopus.broker.security;

import io.octopus.base.interfaces.IAuthenticator;

public class AcceptAllAuthenticator implements IAuthenticator {

    @Override
    public boolean checkValid(String clientId, String username, byte[] password) {
        return true;
    }
}
