package io.octopus.broker.security;

import io.octopus.base.interfaces.IAuthenticator;

/**
 * @author user
 */
public class AcceptAllAuthenticator implements IAuthenticator {

    @Override
    public boolean checkUsername(String clientId, String username, byte[] password) {
        return true;
    }
}
