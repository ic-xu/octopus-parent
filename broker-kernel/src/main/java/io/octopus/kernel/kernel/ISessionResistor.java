package io.octopus.kernel.kernel;

import io.octopus.kernel.kernel.message.KernelPayloadMessage;

import java.util.Set;

/**
 * @author chenxu
 * @version 1
 */
public interface ISessionResistor {

    /**
     * 检索用户
     * @param clientId
     * @return
     */
    ISession retrieve(String clientId);

    /**
     * register -> clientId
     * @param username
     * @param clientId
     */
    void registerUserName(String username, String clientId);

    /**
     * removeClientId
     * @param username
     * @param clientId
     */
    void unRegisterClientIdByUsername(String username, String clientId);

    /**
     * retrieve all clientId
     * @param username username
     * @return set
     */
     Set<String> getClientIdByUsername(String username);

    /**
     * get all session
     *
     * @return sessions
     */
    Set<String> getAllClientId();



    void remove(ISession session);

    /**
     * 创建或者打开一个新的会话信息
     * @param clientId clientId
     * @param username  username
     * @param isClean isClean
     * @param willMsg willMsg
     * @param clientVersion clientVersion
     * @return return
     */
    SessionCreationResult createOrReOpenSession(String clientId, String username, Boolean isClean, KernelPayloadMessage willMsg, int clientVersion);


}
