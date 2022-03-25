package io.octopus.udp.receiver.netty;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.AdaptiveRecvByteBufAllocator;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.util.concurrent.DefaultThreadFactory;
import io.octopus.udp.config.TransportConfig;
import io.octopus.udp.message.DelayMessage;
import io.octopus.udp.message.MessageReceiverListener;
import io.octopus.udp.receiver.netty.handler.NettyUdpDecoderServerHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.locks.ReentrantLock;

public class NettyReceiver{
    Logger logger = LoggerFactory.getLogger(NettyReceiver.class);

    EventLoopGroup workerGroup;

    TransportConfig transportConfig;

    ReentrantLock reentrantLock = new ReentrantLock();

    private MessageReceiverListener messageReceiverListener;


    public NettyReceiver(MessageReceiverListener messageReceiverListener,TransportConfig transportConfig) {
        this.messageReceiverListener = messageReceiverListener;
        this.transportConfig = transportConfig;
        init();
    }

    /**
     *  udp 相关参数
     */
    private ConcurrentHashMap<Long, ByteBuf[]> messageCache = new ConcurrentHashMap<>(128);
    private DelayQueue<DelayMessage> delayMessageQueue = new DelayQueue<>();


    private void init(){
        if (transportConfig.getBooleanProperties("udp.transport.epoll")) {
            logger.info("Netty is using Epoll");
            workerGroup = new EpollEventLoopGroup(new DefaultThreadFactory("work"));
        } else {
            logger.info("Netty is using NIO");
            workerGroup = new NioEventLoopGroup(new DefaultThreadFactory("worker"));
        }

    }


    public void start() throws InterruptedException {
        int port = transportConfig.getIntegerProperties("udp.transport.port",2522);
        String host = transportConfig.getProperties("udp.transport.host");

        ChannelFuture sync = new Bootstrap()
                .group(workerGroup)
                .channel(NioDatagramChannel.class)
                .option(ChannelOption.SO_BROADCAST, true)
                .option(ChannelOption.RCVBUF_ALLOCATOR, new AdaptiveRecvByteBufAllocator(64, 1024, 100 * 65536))
                .handler(new NettyUdpDecoderServerHandler(reentrantLock,transportConfig,messageCache,delayMessageQueue,messageReceiverListener))
                .bind(port).sync();
        logger.info("Server bound to host={}, port={}, protocol={}", host, port, "UDP");
        sync.channel().closeFuture().sync();
    }

}
