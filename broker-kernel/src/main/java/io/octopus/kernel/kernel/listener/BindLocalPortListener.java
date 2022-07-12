package io.octopus.kernel.kernel.listener;

import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.Map;

/**
 * @author chenxu
 * @version 1
 * @date 2022/7/12 17:14
 */
public class BindLocalPortListener implements ChannelFutureListener {
    private final static Logger LOGGER = LoggerFactory.getLogger(BindLocalPortListener.class);

    private final String transportName;
    private final Map<String, Integer> ports;


    public BindLocalPortListener(String transportName, Map<String, Integer> ports) {
        this.transportName = transportName;
        this.ports = ports;
    }

    @Override
    public void operationComplete(ChannelFuture future) throws Exception {
        if (future.isSuccess()) {
            InetSocketAddress localAddress = (InetSocketAddress) future.channel().localAddress();
            Integer port = localAddress.getPort();
            LOGGER.debug("bound {} port: {}", transportName, port);
            ports.put(transportName, port);
        }
    }
}
