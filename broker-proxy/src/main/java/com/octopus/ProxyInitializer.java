package com.octopus;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;

/**
 * @author chenxu
 * @version 1
 * @date 2022/2/25 10:42 上午
 */
public class ProxyInitializer extends ChannelInitializer<SocketChannel> {

    private String hostName;

    private int port;

    public ProxyInitializer(String s, int i) {
        hostName = s;
        port = i;
    }

    @Override
    protected void initChannel(SocketChannel socketChannel) throws Exception {
        socketChannel.pipeline().addLast(
//                new LoggingHandler(LogLevel.INFO),
                new ProxyInboundHandler(hostName, port));
    }
}
