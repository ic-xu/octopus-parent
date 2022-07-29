package io.octopus.kernel.kernel;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.socket.ServerSocketChannel;
import io.netty.channel.socket.SocketChannel;
import io.octopus.config.BrokerConfiguration;
import io.octopus.config.IConfig;
import io.octopus.contants.BrokerConstants;
import io.octopus.kernel.kernel.handler.DropWizardMetricsHandler;
import io.octopus.kernel.kernel.handler.PipelineInitializer;
import io.octopus.kernel.kernel.interceptor.ConnectionNotifyInterceptor;
import io.octopus.kernel.kernel.listener.BindLocalPortListener;
import io.octopus.kernel.kernel.security.IAuthenticator;
import io.octopus.kernel.kernel.security.ReadWriteControl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * @author chenxu
 * @version 1
 * @date 2022/7/12 16:58
 */
public abstract class BaseTransport implements ITransport {

    private Logger logger = LoggerFactory.getLogger(BaseTransport.class);

    private Integer nettySoBacklog = 0;
    private Boolean nettySoReuseaddr = false;
    private Boolean nettyTcpNodelay = false;
    private Boolean nettySoKeepalive = false;
    protected Integer nettyChannelTimeoutSeconds = 0;
    protected Integer maxBytesInMessage = 0;
    private EventLoopGroup bossGroup = null;
    private EventLoopGroup workerGroup = null;


    protected Optional<DropWizardMetricsHandler> metrics = Optional.empty();
    private Class<? extends ServerSocketChannel> channelClass = null;

    protected BrokerConfiguration brokerConfig = null;
    private Map<String, Integer> ports = null;


    public void initialize(EventLoopGroup bossGroup, EventLoopGroup workerGroup, Class<? extends ServerSocketChannel> channelClass,
                           IConfig config, IPostOffice msgDispatcher,
                           ISessionResistor sessionRegistry,
                           Map<String, Integer> ports, IAuthenticator authenticator,
                           List<ConnectionNotifyInterceptor> interceptor,
                           ReadWriteControl readWriteControl) {


        brokerConfig = new BrokerConfiguration(config);

        this.ports = ports;
        nettySoBacklog = config.intProp(BrokerConstants.NETTY_SO_BACKLOG_PROPERTY_NAME, 1024);
        nettySoReuseaddr = config.boolProp(BrokerConstants.NETTY_SO_REUSEADDR_PROPERTY_NAME, true);
        nettyTcpNodelay = config.boolProp(BrokerConstants.NETTY_TCP_NODELAY_PROPERTY_NAME, true);
        nettySoKeepalive = config.boolProp(BrokerConstants.NETTY_SO_KEEPALIVE_PROPERTY_NAME, true);
        nettyChannelTimeoutSeconds = config.intProp(BrokerConstants.NETTY_CHANNEL_TIMEOUT_SECONDS_PROPERTY_NAME, 10);
        maxBytesInMessage = config.intProp(BrokerConstants.NETTY_MAX_BYTES_PROPERTY_NAME, BrokerConstants.DEFAULT_NETTY_MAX_BYTES_IN_MESSAGE);

        this.bossGroup = bossGroup;
        this.workerGroup = workerGroup;
        this.channelClass = channelClass;

        //    globalTrafficShapingHandler = new GlobalTrafficShapingHandler(workerGroup, 1024 * 1024, 5, 10, 5);

        boolean useFineMetrics = config.boolProp(BrokerConstants.METRICS_ENABLE_PROPERTY_NAME, false);
        if (useFineMetrics) {
            DropWizardMetricsHandler metricsHandler = new DropWizardMetricsHandler(msgDispatcher);
            metricsHandler.init(config);
            this.metrics = Optional.of(metricsHandler);
        } else {
            this.metrics = Optional.empty();
        }
    }

    protected void initTcpTransportFactory(String host, Integer port, String protocol, PipelineInitializer pipelineInitializer) {
        logger.debug("Initializing integration. Protocol={}", protocol);
        ServerBootstrap b = new ServerBootstrap();
        b.group(bossGroup, workerGroup)
                .channel(channelClass)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) throws Exception {
                        pipelineInitializer.init(ch);
                    }
                })
                //设置控制tcp 三次握手过程中全链接队列大小。
                .option(ChannelOption.SO_BACKLOG, nettySoBacklog)

                //设置地址可重用（作用是尽早的让地址可用）
                .option(ChannelOption.SO_REUSEADDR, nettySoReuseaddr)

                //TCP 的Nagle 算法。
                .childOption(ChannelOption.TCP_NODELAY, nettyTcpNodelay)

                .childOption(ChannelOption.SO_SNDBUF, maxBytesInMessage * 3)

                .childOption(ChannelOption.SO_KEEPALIVE, nettySoKeepalive);
        try {
            logger.debug("Binding integration. host={}, port={}", host, port);
            // Bind and start to accept incoming connections.
            ChannelFuture bind = b.bind(host, port);

            logger.info("Server bound to host={}, port={}, protocol={}", host, port, protocol);
            bind.sync().addListener(new BindLocalPortListener(protocol, ports)).addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
        } catch (Exception ex) {
            ex.printStackTrace();
            logger.error("An interruptedException was caught while initializing integration. Protocol={}", protocol, ex);
            throw new RuntimeException(ex);
        }
    }
}
