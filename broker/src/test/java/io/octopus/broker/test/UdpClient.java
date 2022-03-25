package io.octopus.broker.test;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.util.CharsetUtil;

import java.net.InetSocketAddress;

class UdpClient {

    Channel channel;

    /**
     * 点对点
     */
//    InetSocketAddress remoteAddress = new InetSocketAddress("localhost", 51888);

    /** 广播地址
     InetSocketAddress remoteAddress = new InetSocketAddress("255.255.255.255", 9000)
     */

    /** 组播地址
     InetSocketAddress remoteAddress = new InetSocketAddress("239.8.8.1", 9000)
     */

    InetSocketAddress remoteAddress = new InetSocketAddress("239.8.8.1", 8888);

    void startClient() throws InterruptedException {

        NioEventLoopGroup group = new NioEventLoopGroup();

        Bootstrap b = new Bootstrap();
        b.group(group)
                .channel(NioDatagramChannel.class)
                .handler(new ChannelInitializer() {
                    protected void initChannel(Channel ch) throws Exception {
                        ch.pipeline()
                                .addLast("recv", new UdpChannelInboundHandler(false));
                    }
                });
        channel = (NioDatagramChannel) b.bind(8888).sync().channel();
    }

    void sendMsg(String msg) throws InterruptedException {

        ByteBuf buf = new UnpooledByteBufAllocator(true).buffer();
        buf.writeCharSequence(msg, CharsetUtil.UTF_8);

        var packet = new DatagramPacket(buf, remoteAddress);

        channel.writeAndFlush(packet).sync();
    }

  public   static void main(String[] args) throws InterruptedException {

        UdpClient client = new UdpClient();

        client.startClient();

        for (int i = 0; i < 5; i++) {
            var msg = "msg "+i;
            System.out.println( "send msg: "+msg);
            client.sendMsg(msg);
        }

        System.out.println("send finish..");
    }
}

