package io.octopus.kernel.kernel.security;

/**
 * username and password checker
 * @author user
 */
public interface IAuthenticator {

    /**
     *
     * @param clientId
     * @param username
     * @param password
     * @return
     */
    boolean checkValid(String clientId, String username, byte[] password);
}
