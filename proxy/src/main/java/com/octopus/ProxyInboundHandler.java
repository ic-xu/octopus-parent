package com.octopus;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;

import java.net.InetSocketAddress;
import java.net.SocketAddress;

/**
 * @author chenxu
 * @version 1
 * @date 2022/2/25 10:44 上午
 */

public class ProxyInboundHandler extends ChannelInboundHandlerAdapter {

    private final String remoteHost;
    private final int remotePort;

    // outboundChannel和inboundChannel使用同一个eventloop
    private Channel outboundChannel;

    public ProxyInboundHandler(String remoteHost, int remotePort) {
        this.remoteHost = remoteHost;
        this.remotePort = remotePort;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        final Channel inboundChannel = ctx.channel();
        // 开启outbound连接
        Bootstrap b = new Bootstrap();
        b.group(inboundChannel.eventLoop())
                .channel(ctx.channel().getClass())
                .handler(new ProxyOutboundHandler(inboundChannel))

                .option(ChannelOption.AUTO_READ, false);
        ;
        SocketAddress address = new InetSocketAddress(remoteHost, remotePort);
        ChannelFuture f = b.connect(address);
        outboundChannel = f.channel();
        f.addListener(future -> {
            if (future.isSuccess()) {
                // 连接建立完毕，读取inbound数据

                ctx.channel().config().setAutoRead(true);
                inboundChannel.read();
            } else {
                // 关闭inbound channel
                inboundChannel.close();
            }
        });
    }

    @Override
    public void channelRead(final ChannelHandlerContext ctx, Object msg) {
        // 将inboundChannel中的消息读取，并写入到outboundChannel
        if (outboundChannel.isActive()) {
            outboundChannel.writeAndFlush(msg).addListener((ChannelFutureListener) future -> {
                if (future.isSuccess()) {
                    // flush成功，读取下一个消息
//                    ctx.channel().read();
                } else {
                    future.channel().close();
                }
            });
        } else {
            ctx.close();
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        if (outboundChannel != null) {
            closeOnFlush(outboundChannel);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        // 异常处理
        cause.printStackTrace();
        closeOnFlush(ctx.channel());
    }


    static void closeOnFlush(Channel ch) {
        if (ch.isActive()) {
            ch.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
        }
    }
}