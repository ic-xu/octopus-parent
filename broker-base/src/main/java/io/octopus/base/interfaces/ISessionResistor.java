package io.octopus.base.interfaces;

import io.netty.channel.Channel;

import java.util.Set;

/**
 * @author chenxu
 * @version 1
 */
public interface ISessionResistor {

    ISession retrieve(String clientId);


    void registerUserName(String username, String clientId);

    void removeUserClientIdByUsername(String username, String clientId);

     Set<String> getClientIdByUsername(String username);



    /**
     * get all session
     *
     * @return sessions
     */
    Set<String> getAllClientId();



    Channel getUdpChannel();

    void setUdpChannel(Channel udpChannel);


}
