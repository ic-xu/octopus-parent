package io.octopus.kernel.kernel.security;

import io.octopus.kernel.kernel.security.IAuthenticator;

/**
 * @author user
 */
public class AcceptAllAuthenticator implements IAuthenticator {

    @Override
    public boolean checkValid(String clientId, String username, byte[] password) {
        return true;
    }
}
