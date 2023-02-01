package io.octopus.kernel.kernel.router;

import java.net.InetSocketAddress;
import java.net.SocketException;
import java.util.Set;

/**
 * 分布式消息路由注册中心
 */
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
