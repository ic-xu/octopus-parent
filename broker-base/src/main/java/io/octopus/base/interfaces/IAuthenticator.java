package io.octopus.base.interfaces;

/**
 * username and password checker
 */
public interface IAuthenticator {

    boolean checkValid(String clientId, String username, byte[] password);
}
