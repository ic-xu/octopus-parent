package io.octopus.base.interfaces;

import java.net.InetSocketAddress;
import java.net.SocketException;
import java.util.Set;

public interface IRouterRegister {

    Set<InetSocketAddress> getAddressByTopic(String topicName);

    void saveAddressAndTopic (String topicName, InetSocketAddress address) ;

    void remove(InetSocketAddress address);

    void stop() throws SocketException;

    /**
     * 检测机器是否存活，如果没有存活
     * @param address
     * @return
     */
    boolean checkIpExit(InetSocketAddress address);
}
