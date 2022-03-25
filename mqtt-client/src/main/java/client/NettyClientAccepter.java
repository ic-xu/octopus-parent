package client;

import client.handler.MqttClientHandler;
import client.process.MqttConnectionProcessFactory;
import client.protocol.ClientProtocolUtil;
import client.protocol.MqttConnectOptions;
import client.utils.NettyChannelUtils;
import io.handler.codec.mqtt.*;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.concurrent.DefaultThreadFactory;

import java.util.concurrent.TimeUnit;


/**
 * //                ByteBuf byteBuf1 = Unpooled.wrappedBuffer("ffffff".getBytes());
 * //                DatagramPacket datagramPacket = new DatagramPacket(byteBuf1, new InetSocketAddress("34.249.122.178", 1883));
 * //                NettyClientAccepter.getInstance().sendUDPMessage(datagramPacket);
 */
public class NettyClientAccepter {


    Bootstrap bootstrap = new Bootstrap();
    NioEventLoopGroup worker = new NioEventLoopGroup(2,new DefaultThreadFactory("worker-pool"));

    private static volatile NettyClientAccepter instance;

    private Channel udpChannel;


    private NettyClientAccepter(MqttConnectionProcessFactory mqttConnectionProcessFactory) {
        init(mqttConnectionProcessFactory);
    }

    public static NettyClientAccepter getInstance(MqttConnectionProcessFactory mqttConnectionProcessFactory) {
        if (null == instance) {
            synchronized (NettyClientAccepter.class) {
                if (null == instance) {
                    instance = new NettyClientAccepter(mqttConnectionProcessFactory);
                }
            }
        }
        return instance;
    }


    private void init(MqttConnectionProcessFactory mqttConnectionProcessFactory) {
        bootstrap.group(worker)
            .channel(NioSocketChannel.class)
            .option(ChannelOption.SO_KEEPALIVE, true)
            .option(ChannelOption.TCP_NODELAY, false)
            //设置链接超时时间
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 50 * 1000)
            .handler(new ChannelInitializer<Channel>() {
                @Override
                protected void initChannel(Channel channel) {

                    channel.pipeline()
                        .addLast("idle-handler",
                            new IdleStateHandler(1000, 300, 3000, TimeUnit.SECONDS)).addLast("decoder", new MqttDecoder())
                        .addLast("encoder", MqttEncoder.INSTANCE)
                        .addLast("mqttHander", new MqttClientHandler(mqttConnectionProcessFactory));
                }
            });
    }

        void doConnectUDP () throws InterruptedException {
            ChannelFuture sync = new Bootstrap()
                .group(worker)
                .channel(NioDatagramChannel.class)
                .option(ChannelOption.SO_BROADCAST, true)
                .handler(new ChannelInitializer<NioDatagramChannel>() {
                    @Override
                    protected void initChannel(NioDatagramChannel nioDatagramChannel) {
                        nioDatagramChannel.pipeline()
                            .addLast(new SimpleChannelInboundHandler<DatagramPacket>() {
                                @Override
                                protected void channelRead0(ChannelHandlerContext channelHandlerContext, DatagramPacket datagramPacket) throws Exception {
                                    datagramPacket.retain();
                                    byte[] buf = new byte[datagramPacket.content().readableBytes()];
                                    datagramPacket.content().readBytes(buf);
                                    System.out.println(new String(buf));
                                }
                            })
                        ;
                    }
                }).bind(0).sync();
            udpChannel = sync.channel();
        }


        public void doConnect (MqttConnectOptions options) throws InterruptedException {
            MqttConnectOptions mqttConnectOptions = options;
            ChannelFuture sync = bootstrap.connect(mqttConnectOptions.getHost(), mqttConnectOptions.getPort()).sync();
            Channel channel =sync.channel();
            if (sync.isSuccess()) {
                NettyChannelUtils.setConnectOption(channel,options);
                channel.writeAndFlush(ClientProtocolUtil.connectMessage(mqttConnectOptions));
                ChannelPipeline pipeline = sync.channel().pipeline();
                pipeline.remove("idle-handler");
                pipeline.addFirst("idle-handler", new IdleStateHandler(mqttConnectOptions.getKeepAliveTime(), mqttConnectOptions.getKeepAliveTime(),
                    mqttConnectOptions.getKeepAliveTime() * 3, TimeUnit.SECONDS));
            } else {
                System.exit(1);
            }
            sync.channel().closeFuture().sync();
        }



        public void startUDP (Integer port) throws InterruptedException {
            new Bootstrap()
                .group(worker)
                .channel(NioDatagramChannel.class)
                .option(ChannelOption.SO_BROADCAST, true)
                .handler(new ChannelInitializer<NioDatagramChannel>() {
                    @Override
                    protected void initChannel(NioDatagramChannel nioDatagramChannel) {
                        nioDatagramChannel.pipeline()
                            .addLast(new SimpleChannelInboundHandler<DatagramPacket>() {
                                @Override
                                protected void channelRead0(ChannelHandlerContext channelHandlerContext, DatagramPacket datagramPacket) throws Exception {
                                    datagramPacket.retain();
                                    byte[] buf = new byte[datagramPacket.content().readableBytes()];
                                    datagramPacket.content().readBytes(buf);
                                    System.out.println(new String(buf));
                                }
                            })
                        ;
                    }
                }).bind(port).sync();
        }


        public void sendUDPMessage (DatagramPacket datagramPacket){
            if (null != udpChannel) {
                udpChannel.writeAndFlush(datagramPacket);
            }
        }
    }

