package io.octopus.broker.test;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelFactory;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.InternetProtocolFamily;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.util.NetUtil;

import java.net.InetSocketAddress;
import java.net.NetworkInterface;

public class UdpServer {

   public static void main(String[] args) {

        UdpServer server = new UdpServer();

        InetSocketAddress address = new InetSocketAddress("239.8.8.1", 51888);

        server.run(address);
    }


    void run(InetSocketAddress groupAddress) {

        EventLoopGroup group = new NioEventLoopGroup();

        try {

            Bootstrap b = new Bootstrap();

            b.group(group)
                    .channelFactory((ChannelFactory<NioDatagramChannel>) () -> new NioDatagramChannel(InternetProtocolFamily.IPv4))
                    .handler(new ChannelInitializer<NioDatagramChannel>() {
                        @Override
                        public void initChannel(NioDatagramChannel ch) throws Exception {
                            ch.pipeline().addLast(new UdpChannelInboundHandler(true));
                        }
                    });

            NioDatagramChannel ch = (NioDatagramChannel) b.bind(groupAddress.getPort()).sync().channel();


            NetworkInterface ni = NetUtil.LOOPBACK_IF;

            System.out.println(ni.getName()+":"+ni.getDisplayName());


            ch.joinGroup(groupAddress, ni).sync();

            System.out.println("udp server("+groupAddress.getHostName()+":"+groupAddress.getPort()+") is running...");


            ch.closeFuture().await();

        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            group.shutdownGracefully();
        }
    }
}
