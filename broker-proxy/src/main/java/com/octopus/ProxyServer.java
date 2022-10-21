package com.octopus;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;

/**
 * @author chenxu
 * @version 1
 * @date 2022/2/25 10:41 上午
 */
public class ProxyServer {

    public static void main(String[] args) throws InterruptedException {
        String host = "ws-live-push-cowork-airport.test.maxhub.vip";
        host="live-gataway.test.maxhub.vip";
        EventLoopGroup bossGroup = new NioEventLoopGroup(1);
        EventLoopGroup workerGroup = new NioEventLoopGroup();
        ServerBootstrap b = new ServerBootstrap();
        b.group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .handler(new LoggingHandler(LogLevel.INFO))
                .childOption(ChannelOption.AUTO_READ, false)
                .childHandler(new ProxyInitializer(host, 8989))
//                .childHandler(new SimpleDumpProxyInitializer("127.0.0.1", 1945))
                .bind(1999).sync().channel().closeFuture().sync();
    }

}
