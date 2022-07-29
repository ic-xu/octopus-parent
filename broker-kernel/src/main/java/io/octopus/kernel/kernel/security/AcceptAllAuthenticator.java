package io.octopus.kernel.kernel.security;

/**
 * @author user
 */
public class AcceptAllAuthenticator implements IAuthenticator {

    @Override
    public boolean checkValid(String clientId, String username, byte[] password) {
        return true;
    }
}
