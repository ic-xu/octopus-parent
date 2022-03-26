package io.octopus.base.interfaces;

/**
 * username and password checker
 * @author user
 */
public interface IAuthenticator {

    /**
     * 检测用户合法性
     * @param clientId clientId
     * @param username username
     * @param password password
     * @return boolean
     */
    boolean checkUsername(String clientId, String username, byte[] password);
}
