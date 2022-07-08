//package io.octopus.broker;
//
//import io.netty.handler.logging.LogLevel;
//import io.netty.handler.logging.LoggingHandler;
//import io.netty.handler.traffic.GlobalTrafficShapingHandler;
//import io.octopus.broker.handler.*;
//import io.octopus.broker.session.SessionRegsistor;
//import io.octopus.base.config.BrokerConfiguration;
//import io.octopus.base.config.IConfig;
//import io.octopus.base.contants.BrokerConstants;
//import io.octopus.broker.metrics.*;
//import io.octopus.base.interfaces.ISslContextCreator;
//import io.octopus.scala.mqtt.broker.handler.UdpMQTTHandler;
//import io.octopus.udp.message.DelayMessage;
//import io.octopus.udp.message.MessageReceiverListener;
//import io.octopus.udp.config.TransportConfig;
//import io.netty.bootstrap.Bootstrap;
//import io.netty.bootstrap.ServerBootstrap;
//import io.netty.buffer.ByteBuf;
//import io.netty.channel.*;
//import io.netty.channel.epoll.EpollEventLoopGroup;
//import io.netty.channel.epoll.EpollServerSocketChannel;
//import io.netty.channel.nio.NioEventLoopGroup;
//import io.netty.channel.socket.ServerSocketChannel;
//import io.netty.channel.socket.SocketChannel;
//import io.netty.channel.socket.nio.NioDatagramChannel;
//import io.netty.channel.socket.nio.NioServerSocketChannel;
//import io.netty.handler.codec.http.*;
//import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
//import io.handler.codec.mqtt.MqttDecoder;
//import io.handler.codec.mqtt.MqttEncoder;
//import io.netty.handler.ssl.SslContext;
//import io.netty.handler.ssl.SslHandler;
//import io.netty.handler.stream.ChunkedWriteHandler;
//import io.netty.handler.timeout.IdleStateHandler;
//import io.netty.util.concurrent.DefaultThreadFactory;
//import io.netty.util.concurrent.Future;
//import io.octopus.udp.receiver.netty.handler.NettyUdpDecoderServerHandler;
//import io.octopus.base.subscriptions.ISubscriptionsDirectory;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//
//import javax.net.ssl.SSLEngine;
//import java.net.InetSocketAddress;
//import java.net.SocketAddress;
//import java.util.HashMap;
//import java.util.Map;
//import java.util.Optional;
//import java.util.concurrent.ConcurrentHashMap;
//import java.util.concurrent.DelayQueue;
//import java.util.concurrent.TimeUnit;
//import java.util.concurrent.locks.ReentrantLock;
//
//import static io.octopus.base.contants.BrokerConstants.*;
//import static io.octopus.base.contants.BrokerConstants.UDP_PORT_PROPERTY_NAME;
//import static io.netty.channel.ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE;
//
//public class NettyAcceptor {
//
//    private static final String MQTT_SUBPROTOCOL_CSV_LIST = "mqtt, mqttv3.1, mqttv3.1.1";
//    public static final String PLAIN_MQTT_PROTO = "TCP MQTT";
//    public static final String SSL_MQTT_PROTO = "SSL MQTT";
//
//    /*
//     * udp 相关参数
//     */
//    private final ConcurrentHashMap<Long, ByteBuf[]> messageCache = new ConcurrentHashMap<>(128);
//    private final DelayQueue<DelayMessage> delayMessageQueue = new DelayQueue<>();
//    private final MessageReceiverListener messageReceiverListener;
//    private final ReentrantLock reentrantLock = new ReentrantLock();
//
//    //流量整形
//    private GlobalTrafficShapingHandler globalTrafficShapingHandler;
//
//
//    private interface PipelineInitializer {
//        /*
//         * config channel Pipeline
//         */
//        void init(SocketChannel channel) throws Exception;
//    }
//
//
//    private class LocalPortReaderFutureListener implements ChannelFutureListener {
//        private final String transportName;
//
//        LocalPortReaderFutureListener(String transportName) {
//            this.transportName = transportName;
//        }
//
//        @Override
//        public void operationComplete(ChannelFuture future) {
//            if (future.isSuccess()) {
//                final SocketAddress localAddress = future.channel().localAddress();
//                if (localAddress instanceof InetSocketAddress) {
//                    InetSocketAddress inetAddress = (InetSocketAddress) localAddress;
//                    logger.debug("bound {} port: {}", transportName, inetAddress.getPort());
//                    int port = inetAddress.getPort();
//                    ports.put(transportName, port);
//                }
//            }
//        }
//    }
//
//    private final Logger logger = LoggerFactory.getLogger(NettyAcceptor.class);
//
//    private EventLoopGroup bossGroup;
//    private EventLoopGroup workerGroup;
//    private final Map<String, Integer> ports = new HashMap<>();
//    private final BytesMetricsCollector bytesMetricsCollector = new BytesMetricsCollector();
//    private final MessageMetricsCollector metricsCollector = new MessageMetricsCollector();
//    private Optional<? extends ChannelInboundHandler> metrics;
//
//    private int nettySoBacklog;
//    private boolean nettySoReuseaddr;
//    private boolean nettyTcpNodelay;
//    private boolean nettySoKeepalive;
//    private int nettyChannelTimeoutSeconds;
//    private int maxBytesInMessage;
//    private BrokerConfiguration brokerConfig;
//
//    private Class<? extends ServerSocketChannel> channelClass;
//
//    public NettyAcceptor(BrokerConfiguration brokerConfig, MessageReceiverListener messageReceiverListener) {
//        this.brokerConfig = brokerConfig;
//        this.messageReceiverListener = messageReceiverListener;
//    }
//
//    /**
//     * @param mqttHandler   handler
//     * @param config        config
//     * @param sslCtxCreator ssl
//     * @param msgDispatcher 后面新增的参数，用于发送 Metrics 主题消息
//     */
//    public void initialize(NettyMQTTHandler mqttHandler, IConfig config, ISslContextCreator sslCtxCreator,
//                           MsgDispatcher msgDispatcher, SessionRegsistor sessions, ISubscriptionsDirectory subscriptionsDirectory) throws InterruptedException {
//        logger.debug("Initializing Netty acceptor");
//
//        nettySoBacklog = config.intProp(BrokerConstants.NETTY_SO_BACKLOG_PROPERTY_NAME, 1024);
//        nettySoReuseaddr = config.boolProp(BrokerConstants.NETTY_SO_REUSEADDR_PROPERTY_NAME, true);
//        nettyTcpNodelay = config.boolProp(BrokerConstants.NETTY_TCP_NODELAY_PROPERTY_NAME, true);
//        nettySoKeepalive = config.boolProp(BrokerConstants.NETTY_SO_KEEPALIVE_PROPERTY_NAME, true);
//        nettyChannelTimeoutSeconds = config.intProp(BrokerConstants.NETTY_CHANNEL_TIMEOUT_SECONDS_PROPERTY_NAME, 10);
//        maxBytesInMessage = config.intProp(BrokerConstants.NETTY_MAX_BYTES_PROPERTY_NAME,
//                BrokerConstants.DEFAULT_NETTY_MAX_BYTES_IN_MESSAGE);
//
//        boolean epoll = config.boolProp(BrokerConstants.NETTY_EPOLL_PROPERTY_NAME, false);
//        if (epoll) {
//            logger.info("Netty is using Epoll");
//            bossGroup = new EpollEventLoopGroup(new DefaultThreadFactory("boss"));
//            workerGroup = new EpollEventLoopGroup(new DefaultThreadFactory("work"));
//            channelClass = EpollServerSocketChannel.class;
//        } else {
//            logger.info("Netty is using NIO");
//            bossGroup = new NioEventLoopGroup(new DefaultThreadFactory("boss"));
//            workerGroup = new NioEventLoopGroup(new DefaultThreadFactory("worker"));
//            channelClass = NioServerSocketChannel.class;
//        }
//
//        globalTrafficShapingHandler = new GlobalTrafficShapingHandler(workerGroup, 1024 * 1024,
//                5, 10, 5);
//
//        final boolean useFineMetrics = config.boolProp(METRICS_ENABLE_PROPERTY_NAME, false);
//        if (useFineMetrics) {
//            DropWizardMetricsHandler metricsHandler = new DropWizardMetricsHandler(msgDispatcher);
//            metricsHandler.init(config);
//            this.metrics = Optional.of(metricsHandler);
//        } else {
//            this.metrics = Optional.empty();
//        }
//
//        final boolean useBugSnag = config.boolProp(BUGSNAG_ENABLE_PROPERTY_NAME, false);
//
//        /*
//         * init SSL netty server
//         */
//        if (securityPortsConfigured(config)) {
//            SslContext sslContext = sslCtxCreator.initSSLContext();
//            if (sslContext == null) {
//                logger.error("Can't initialize SSLHandler layer! Exiting, check your configuration of jks");
//                return;
//            }
//            initializeSSLTCPTransport(mqttHandler, config, sslContext);
//            initializeWSSTransport(mqttHandler, config, sslContext);
//
//            /* 证书没有经过签证的证书，所以https 不能使用 */
////            initializeHttpsTransport(mqttHandler, config,sessions, sslContext);
//        }
//
//        /*
//         * init netty server
//         */
//        initializePlainTCPTransport(mqttHandler, config);
//        initializeWebSocketTransport(mqttHandler, config);
//
//        /*
//         * init http/https server
//         */
//        initializeHttpTransport(config, sessions, subscriptionsDirectory);
//
//        /*
//         * init UDP
//         */
//        initializeUDPTransport(config, sessions, subscriptionsDirectory);
//
//        // init udo internal
//        initializeUDPInternalTransport(config, sessions);
//
//    }
//
//    private boolean securityPortsConfigured(IConfig props) {
//        String sslTcpPortProp = props.getProperty(BrokerConstants.SSL_PORT_PROPERTY_NAME);
//        String wssPortProp = props.getProperty(BrokerConstants.WSS_PORT_PROPERTY_NAME);
//        return sslTcpPortProp != null || wssPortProp != null;
//    }
//
//
//    private boolean customProtocolConfig(IConfig props) {
//        String sslTcpPortProp = props.getProperty(BrokerConstants.SSL_PORT_PROPERTY_NAME);
//        String wssPortProp = props.getProperty(BrokerConstants.WSS_PORT_PROPERTY_NAME);
//        return sslTcpPortProp != null || wssPortProp != null;
//    }
//
//    private void initTcpTransportFactory(String host, int port, String protocol, final PipelineInitializer pipelineInitializer) {
//        logger.info("Initializing integration. Protocol={}", protocol);
//        ServerBootstrap b = new ServerBootstrap();
//        b.group(bossGroup, workerGroup).channel(channelClass)
//                .childHandler(new ChannelInitializer<SocketChannel>() {
//
//                    @Override
//                    public void initChannel(SocketChannel ch) throws Exception {
//                        pipelineInitializer.init(ch);
//                    }
//                })
//
//                //设置控制tcp 三次握手过程中全链接队列大小。
//                .option(ChannelOption.SO_BACKLOG, nettySoBacklog)
//
//                //设置地址可重用（作用是尽早的让地址可用）
//                .option(ChannelOption.SO_REUSEADDR, nettySoReuseaddr)
//
//                //TCP 的Nagle 算法。
//                .childOption(ChannelOption.TCP_NODELAY, nettyTcpNodelay)
//
//                .childOption(ChannelOption.SO_SNDBUF, maxBytesInMessage * 3)
//
//                .childOption(ChannelOption.SO_KEEPALIVE, nettySoKeepalive);
//        try {
//            logger.debug("Binding integration. host={}, port={}", host, port);
//            // Bind and start to accept incoming connections.
//            ChannelFuture f = b.bind(host, port);
//            logger.info("Server bound to host={}, port={}, protocol={}", host, port, protocol);
//            f.sync().addListener(new LocalPortReaderFutureListener(protocol))
//                    .addListener(FIRE_EXCEPTION_ON_FAILURE);
//
//        } catch (Exception ex) {
//            logger.error("An interruptedException was caught while initializing integration. Protocol={}", protocol, ex);
//            throw new RuntimeException(ex);
//        }
//    }
//
//    public int getPort() {
//        return ports.computeIfAbsent(PLAIN_MQTT_PROTO, i -> 0);
//    }
//
//    public int getSslPort() {
//        return ports.computeIfAbsent(SSL_MQTT_PROTO, i -> 0);
//    }
//
//    private void initializePlainTCPTransport(NettyMQTTHandler handler, IConfig config) {
//        logger.debug("Configuring TCP MQTT transport");
//        final OctopusIdleTimeoutHandler timeoutHandler = new OctopusIdleTimeoutHandler();
//        String host = config.getProperty(BrokerConstants.HOST_PROPERTY_NAME);
//        String tcpPortProp = config.getProperty(PORT_PROPERTY_NAME, DISABLED_PORT_BIND);
//        if (DISABLED_PORT_BIND.equals(tcpPortProp)) {
//            logger.info("Property {} has been set to {}. TCP MQTT will be disabled", BrokerConstants.PORT_PROPERTY_NAME,
//                    DISABLED_PORT_BIND);
//            return;
//        }
//        int port = Integer.parseInt(tcpPortProp);
//        initTcpTransportFactory(host, port, PLAIN_MQTT_PROTO, channel -> {
//            ChannelPipeline pipeline = channel.pipeline();
//            configureMQTTPipeline(pipeline, timeoutHandler, handler);
//        });
//    }
//
//    private void initializeUDPTransport(IConfig config, SessionRegsistor sessions, ISubscriptionsDirectory subscriptionsDirectory) throws InterruptedException {
//        logger.debug("Configuring UDP MQTT transport");
//        String host = config.getProperty(BrokerConstants.HOST_PROPERTY_NAME);
//        Integer portProp = config.getIntegerProperty(PORT_PROPERTY_NAME, 1883);
//        ChannelFuture sync = new Bootstrap()
//                .group(workerGroup)
//                .channel(NioDatagramChannel.class)
//                .option(ChannelOption.SO_BROADCAST, true)
//                .option(ChannelOption.RCVBUF_ALLOCATOR, new AdaptiveRecvByteBufAllocator(64, 1024, 100 * 65536))
//                .handler(new UdpMQTTHandler(sessions, subscriptionsDirectory))
//                .bind(portProp).sync();
//        logger.info("Server bound to host={}, port={}, protocol={}", host, portProp, "UDP");
//        sessions.setUdpChannel(sync.channel());
//    }
//
//    private void configureMQTTPipeline(ChannelPipeline pipeline, OctopusIdleTimeoutHandler timeoutHandler,
//                                       NettyMQTTHandler handler) {
////        pipeline.addFirst("globalTrafficshaping", globalTrafficShapingHandler);
//        pipeline.addFirst("idleStateHandler", new IdleStateHandler(nettyChannelTimeoutSeconds, 0, 0));
//        pipeline.addAfter("idleStateHandler", "timeoutHandler", timeoutHandler);
//        if (brokerConfig.isOpenNettyLogger()) {
//            pipeline.addLast("logger", new LoggingHandler("Netty", LogLevel.INFO));
//            logger.info("pipeline add NettyLogger Handler");
//        }
//        pipeline.addFirst("byteMetrics", new BytesMetricsHandler(bytesMetricsCollector));
//        if (!brokerConfig.isImmediateBufferFlush()) {
//            pipeline.addLast("autoFlush", new AutoFlushHandler(10, TimeUnit.MILLISECONDS));
//            logger.info("pipeline add autoFlush Handler");
//        }
//        pipeline.addLast("decoder", new MqttDecoder(maxBytesInMessage));
//        pipeline.addLast("encoder", MqttEncoder.INSTANCE);
//        pipeline.addLast("metrics", new MessageMetricsHandler(metricsCollector));
//        pipeline.addLast("messageLogger", new MQTTMessageLoggerHandler());
//
//        metrics.ifPresent(channelInboundHandler -> pipeline.addLast("wizardMetrics", channelInboundHandler));
//        pipeline.addLast("handler", handler);
//    }
//
//    private void initializeWebSocketTransport(final NettyMQTTHandler handler, IConfig config) {
//        logger.debug("Configuring Websocket MQTT transport");
//        String webSocketPortProp = config.getProperty(WEB_SOCKET_PORT_PROPERTY_NAME, DISABLED_PORT_BIND);
//        if (DISABLED_PORT_BIND.equals(webSocketPortProp)) {
//            // Do nothing no WebSocket configured
//            logger.info("Property {} has been setted to {}. Websocket MQTT will be disabled",
//                    BrokerConstants.WEB_SOCKET_PORT_PROPERTY_NAME, DISABLED_PORT_BIND);
//            return;
//        }
//        int port = Integer.parseInt(webSocketPortProp);
//
//        final OctopusIdleTimeoutHandler timeoutHandler = new OctopusIdleTimeoutHandler();
//
//        String host = config.getProperty(BrokerConstants.HOST_PROPERTY_NAME);
//        String path = config.getProperty(BrokerConstants.WEB_SOCKET_PATH_PROPERTY_NAME, BrokerConstants.WEBSOCKET_PATH);
//        int maxFrameSize = config.intProp(BrokerConstants.WEB_SOCKET_MAX_FRAME_SIZE_PROPERTY_NAME, 65536);
//        initTcpTransportFactory(host, port, "Websocket MQTT", channel -> {
//            ChannelPipeline pipeline = channel.pipeline();
//            pipeline.addLast(new HttpServerCodec());
//            pipeline.addLast("aggregator", new HttpObjectAggregator(maxFrameSize));
//            pipeline.addLast("webSocketHandler",
//                    new WebSocketServerProtocolHandler(path, MQTT_SUBPROTOCOL_CSV_LIST, false, maxFrameSize));
//            pipeline.addLast("ContinuationWebSocketFrameHandler", new ContinuationWebSocketFrameHandler());
//            pipeline.addLast("ws2bytebufDecoder", new WebSocketDecoder());
//            pipeline.addLast("bytebuf2wsEncoder", new WebSocketEncoder());
//            configureMQTTPipeline(pipeline, timeoutHandler, handler);
//        });
//    }
//
//
//    private void initializeHttpTransport(IConfig config, SessionRegsistor sessions, ISubscriptionsDirectory subscriptionsDirectory) {
//        logger.debug("Configuring HTTP MQTT transport");
//        String httpPortProp = config.getProperty(HTTP_PORT, "8090");
//        int port = Integer.parseInt(httpPortProp);
//        String host = config.getProperty(BrokerConstants.HOST_PROPERTY_NAME);
//        initTcpTransportFactory(host, port, "HTTP MQTT", channel -> {
//            ChannelPipeline p = channel.pipeline();
//            /*
//             * 或者使用HttpRequestDecoder & HttpResponseEncoder
//             */
//            p.addLast(new HttpServerCodec());
//            /*
//             * 在处理POST消息体时需要加上
//             */
//            p.addLast(new HttpObjectAggregator(10 * 1024 * 1024));
//            p.addLast(new HttpServerExpectContinueHandler());
//            p.addLast(new ChunkedWriteHandler());
//            p.addLast(new NettyHttpServerHandler(sessions, subscriptionsDirectory));
//        });
//    }
//
//
//    /**
//     * 这个证书通常不可用，因为是私有的证书，所有实际上没有什么用。所以这个方法没有也一样
//     *
//     * @param handler    handler
//     * @param config     config
//     * @param sslContext ssl
//     */
//    private void initializeHttpsTransport(final NettyMQTTHandler handler, IConfig config,
//                                          ISubscriptionsDirectory subscriptionsDirectory,
//                                          SessionRegsistor sessions, SslContext sslContext) {
//        logger.debug("Configuring HTTPS MQTT transport");
//        String httpsPortProp = config.getProperty(HTTPS_PORT, "9999");
//        int port = Integer.parseInt(httpsPortProp);
//        String host = config.getProperty(BrokerConstants.HOST_PROPERTY_NAME);
//        String sNeedsClientAuth = config.getProperty(BrokerConstants.NEED_CLIENT_AUTH, "false");
//        final boolean needsClientAuth = Boolean.parseBoolean(sNeedsClientAuth);
//        initTcpTransportFactory(host, port, "HTTPS MQTT", channel -> {
//            ChannelPipeline p = channel.pipeline();
//            p.addLast("ssl", createSslHandler(channel, sslContext, needsClientAuth));
//            /*
//             * 或者使用HttpRequestDecoder & HttpResponseEncoder
//             */
//            p.addLast(new HttpServerCodec());
//            /*
//             * 在处理POST消息体时需要加上
//             */
//            p.addLast(new HttpObjectAggregator(10 * 1024 * 1024));
//            p.addLast(new HttpServerExpectContinueHandler());
//            p.addLast(new ChunkedWriteHandler());
//            p.addLast(new NettyHttpServerHandler(sessions, subscriptionsDirectory));
//        });
//    }
//
//    private void initializeUDPInternalTransport(IConfig props, SessionRegsistor sessions) throws InterruptedException {
//        logger.debug("Configuring UDP Internal MQTT transport");
//        String host = props.getProperty(BrokerConstants.HOST_PROPERTY_NAME);
//        int udpPort = props.getIntegerProperty(UDP_PORT_PROPERTY_NAME, BrokerConstants.UDP_TRANSPORT_DEFAULT_PORT);
//        ChannelFuture sync = new Bootstrap()
//                .group(workerGroup)
//                .channel(NioDatagramChannel.class)
//                .option(ChannelOption.SO_BROADCAST, true)
//                .option(ChannelOption.RCVBUF_ALLOCATOR, new AdaptiveRecvByteBufAllocator(64, 1024, 100 * 65536))
//                .handler(new NettyUdpDecoderServerHandler(reentrantLock, new TransportConfig(), messageCache, delayMessageQueue, messageReceiverListener))
//                .bind(udpPort).sync();
//        logger.info("Server bound to host={}, port={}, protocol={}", host, udpPort, "UDP");
//        sessions.setUdpChannel(sync.channel());
//    }
//
//
//    private void initializeSSLTCPTransport(NettyMQTTHandler handler, IConfig props, SslContext sslContext) {
//        logger.debug("Configuring SSL MQTT transport");
//        String sslPortProp = props.getProperty(SSL_PORT_PROPERTY_NAME, DISABLED_PORT_BIND);
//        if (DISABLED_PORT_BIND.equals(sslPortProp)) {
//            // Do nothing no SSL configured
//            logger.info("Property {} has been set to {}. SSL MQTT will be disabled",
//                    BrokerConstants.SSL_PORT_PROPERTY_NAME, DISABLED_PORT_BIND);
//            return;
//        }
//
//        int sslPort = Integer.parseInt(sslPortProp);
//        logger.debug("Starting SSL on port {}", sslPort);
//
//        final OctopusIdleTimeoutHandler timeoutHandler = new OctopusIdleTimeoutHandler();
//        String host = props.getProperty(BrokerConstants.HOST_PROPERTY_NAME);
//        String sNeedsClientAuth = props.getProperty(BrokerConstants.NEED_CLIENT_AUTH, "false");
//        final boolean needsClientAuth = Boolean.parseBoolean(sNeedsClientAuth);
//        initTcpTransportFactory(host, sslPort, SSL_MQTT_PROTO, channel -> {
//            ChannelPipeline pipeline = channel.pipeline();
//            pipeline.addLast("ssl", createSslHandler(channel, sslContext, needsClientAuth));
//            configureMQTTPipeline(pipeline, timeoutHandler, handler);
//        });
//    }
//
//    private void initializeWSSTransport(NettyMQTTHandler handler, IConfig props, SslContext sslContext) {
//        logger.debug("Configuring secure websocket MQTT transport");
//        String sslPortProp = props.getProperty(WSS_PORT_PROPERTY_NAME, DISABLED_PORT_BIND);
//        if (DISABLED_PORT_BIND.equals(sslPortProp)) {
//            // Do nothing no SSL configured
//            logger.info("Property {} has been set to {}. Secure websocket MQTT will be disabled",
//                    BrokerConstants.WSS_PORT_PROPERTY_NAME, DISABLED_PORT_BIND);
//            return;
//        }
//        int sslPort = Integer.parseInt(sslPortProp);
//        final OctopusIdleTimeoutHandler timeoutHandler = new OctopusIdleTimeoutHandler();
//        String host = props.getProperty(BrokerConstants.HOST_PROPERTY_NAME);
//        String path = props.getProperty(BrokerConstants.WEB_SOCKET_PATH_PROPERTY_NAME, BrokerConstants.WEBSOCKET_PATH);
//        int maxFrameSize = props.intProp(BrokerConstants.WEB_SOCKET_MAX_FRAME_SIZE_PROPERTY_NAME, 65536);
//        String sNeedsClientAuth = props.getProperty(BrokerConstants.NEED_CLIENT_AUTH, "false");
//        final boolean needsClientAuth = Boolean.parseBoolean(sNeedsClientAuth);
//        initTcpTransportFactory(host, sslPort, "Secure websocket", channel -> {
//
//            ChannelPipeline pipeline = channel.pipeline();
//            pipeline.addLast("ssl", createSslHandler(channel, sslContext, needsClientAuth));
//            pipeline.addLast("httpEncoder", new HttpResponseEncoder());
//            pipeline.addLast("httpDecoder", new HttpRequestDecoder());
//            pipeline.addLast("aggregator", new HttpObjectAggregator(65536));
//            pipeline.addLast("webSocketHandler",
//                    new WebSocketServerProtocolHandler(path, MQTT_SUBPROTOCOL_CSV_LIST, false, maxFrameSize));
//            pipeline.addLast("ws2bytebufDecoder", new WebSocketDecoder());
//            pipeline.addLast("bytebuf2wsEncoder", new WebSocketEncoder());
//
//            configureMQTTPipeline(pipeline, timeoutHandler, handler);
//        });
//    }
//
//
//    public void close() {
//        logger.debug("Closing Netty acceptor...");
//        if (workerGroup == null || bossGroup == null) {
//            logger.error("Netty acceptor is not initialized");
//            throw new IllegalStateException("Invoked close on an Acceptor that wasn't initialized");
//        }
//        Future<?> workerWaiter = workerGroup.shutdownGracefully();
//        Future<?> bossWaiter = bossGroup.shutdownGracefully();
//
//        /*
//         * We shouldn't raise an IllegalStateException if we are interrupted. If we did so, the
//         * broker is not shut down properly.
//         */
//        logger.info("Waiting for worker and boss event loop groups to terminate...");
//        try {
//            workerWaiter.await(10, TimeUnit.SECONDS);
//            bossWaiter.await(10, TimeUnit.SECONDS);
//        } catch (InterruptedException iex) {
//            logger.warn("An InterruptedException was caught while waiting for event loops to terminate...");
//        }
//
//        if (!workerGroup.isTerminated()) {
//            logger.warn("Forcing shutdown of worker event loop...");
//            workerGroup.shutdownGracefully(0L, 0L, TimeUnit.MILLISECONDS);
//        }
//
//        if (!bossGroup.isTerminated()) {
//            logger.warn("Forcing shutdown of boss event loop...");
//            bossGroup.shutdownGracefully(0L, 0L, TimeUnit.MILLISECONDS);
//        }
//
//        MessageMetrics metrics = metricsCollector.computeMetrics();
//        BytesMetrics bytesMetrics = bytesMetricsCollector.computeMetrics();
//        logger.info("Metrics messages[read={}, write={}] bytes[read={}, write={}]", metrics.messagesRead(),
//                metrics.messagesWrote(), bytesMetrics.readBytes(), bytesMetrics.wroteBytes());
//    }
//
//    private ChannelHandler createSslHandler(SocketChannel channel, SslContext sslContext, boolean needsClientAuth) {
//        SSLEngine sslEngine = sslContext.newEngine(
//                channel.alloc(),
//                channel.remoteAddress().getHostString(),
//                channel.remoteAddress().getPort());
//        sslEngine.setUseClientMode(false);
//        if (needsClientAuth) {
//            sslEngine.setNeedClientAuth(true);
//        }
//        return new SslHandler(sslEngine);
//    }
//}
