package io.octopus.broker.security;

public class AcceptAllAuthenticator implements IAuthenticator {

    @Override
    public boolean checkValid(String clientId, String username, byte[] password) {
        return true;
    }
}
