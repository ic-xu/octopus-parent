package io.octopus.kernel.kernel;

import io.netty.channel.EventLoopGroup;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollServerSocketChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.ServerSocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.util.concurrent.DefaultThreadFactory;
import io.netty.util.concurrent.Future;
import io.octopus.config.IConfig;
import io.octopus.contants.BrokerConstants;
import io.octopus.kernel.kernel.interceptor.ConnectionNotifyInterceptor;
import io.octopus.kernel.kernel.metrics.BytesMetrics;
import io.octopus.kernel.kernel.metrics.BytesMetricsCollector;
import io.octopus.kernel.kernel.metrics.MessageMetrics;
import io.octopus.kernel.kernel.metrics.MessageMetricsCollector;
import io.octopus.kernel.kernel.security.IAuthenticator;
import io.octopus.kernel.kernel.security.ReadWriteControl;
import io.octopus.kernel.kernel.subscriptions.ISubscriptionsDirectory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.ServiceLoader;
import java.util.concurrent.TimeUnit;

/**
 * @author chenxu
 * @version 1
 * @date 2022/7/12 18:38
 */
public class TransportBootstrap {


    private static final Logger LOGGER = LoggerFactory.getLogger(TransportBootstrap.class);
    private final java.util.Map<String, Integer> ports = new HashMap<>();
    private EventLoopGroup bossGroup = null;
    private EventLoopGroup workerGroup = null;
    private final BytesMetricsCollector bytesMetricsCollector = new BytesMetricsCollector();
    private final MessageMetricsCollector metricsCollector = new MessageMetricsCollector();
    private Class<? extends ServerSocketChannel> channelClass = null;
    private final java.util.List<ITransport> protocolCovertHandlerList = new ArrayList<>();

    private final IAuthenticator authenticator;
    private final List<ConnectionNotifyInterceptor> interceptor;
    private final ReadWriteControl readWriteControl;

    public TransportBootstrap(IAuthenticator authenticator, List<ConnectionNotifyInterceptor> interceptor, ReadWriteControl readWriteControl) {
        this.authenticator = authenticator;
        this.interceptor = interceptor;
        this.readWriteControl = readWriteControl;
    }

    /**
     * init nettyAcceptor container
     *
     * @param config                 config
     * @param postOffice             msgdispatcher
     * @param sessionRegistry        sessions
     * @param subscriptionsDirectory subs
     */
    public void initialize(IConfig config, IPostOffice postOffice, ISessionResistor
            sessionRegistry, ISubscriptionsDirectory subscriptionsDirectory) {

        init(config);

        bootstrap(config, sessionRegistry, subscriptionsDirectory, postOffice);

    }


    /**
     * init config
     *
     * @param config config
     */
    private void init(IConfig config) {

        boolean epoll = config.boolProp(BrokerConstants.NETTY_EPOLL_PROPERTY_NAME, false);
        if (epoll) {
            LOGGER.info("Netty is using Epoll");
            bossGroup = new EpollEventLoopGroup(Runtime.getRuntime().availableProcessors(), new DefaultThreadFactory("boss"));
            workerGroup = new EpollEventLoopGroup(new DefaultThreadFactory("work"));
            channelClass = EpollServerSocketChannel.class;
        } else {
            LOGGER.info("Netty is using NIO");
            bossGroup = new NioEventLoopGroup(Runtime.getRuntime().availableProcessors(), new DefaultThreadFactory("boss"));
            workerGroup = new NioEventLoopGroup(new DefaultThreadFactory("worker"));
            channelClass = NioServerSocketChannel.class;
        }

        ServiceLoader<ITransport> transports = ServiceLoader.load(ITransport.class);
        transports.forEach(transport -> {
            LOGGER.info("[SPI] === load ITransport.class [ {} ]", transport.getClass().getName());
            protocolCovertHandlerList.add(transport);
        });
    }


    /**
     * bootstrap config
     *
     * @param config                 config
     * @param sessionFactory         sessions
     * @param subscriptionsDirectory subs
     * @param postOffice             postoffice
     */
    private void bootstrap(IConfig config, ISessionResistor sessionFactory, ISubscriptionsDirectory subscriptionsDirectory, IPostOffice postOffice) {
        protocolCovertHandlerList.forEach(protocolCovertHandler -> {
            try {
                protocolCovertHandler.initProtocol(bossGroup, workerGroup, channelClass, config, sessionFactory,
                        subscriptionsDirectory, postOffice, ports, authenticator, interceptor, readWriteControl);
            } catch (Exception e) {
                e.printStackTrace();
                LOGGER.error(e.getMessage());
            }
        });
    }


    public void close() {
        LOGGER.debug("Closing Netty acceptor...");
        if (workerGroup == null || bossGroup == null) {
            LOGGER.error("Netty acceptor is not initialized");
            throw new IllegalStateException("Invoked close on an Acceptor that wasn't initialized");
        }
        Future<?> workerWaiter = workerGroup.shutdownGracefully();
        Future<?> bossWaiter = bossGroup.shutdownGracefully();
        /*
         * We shouldn't raise an IllegalStateException if we are interrupted. If we did so, the
         * broker is not shut down properly.
         */
        LOGGER.info("Waiting for worker and boss event loop groups to terminate...");
        try {
            workerWaiter.await(10, TimeUnit.SECONDS);
            bossWaiter.await(10, TimeUnit.SECONDS);
        } catch (Exception iex) {
            LOGGER.warn("An InterruptedException was caught while waiting for event loops to terminate...");
            LOGGER.warn(iex.getMessage());
        }
        if (!workerGroup.isTerminated()) {
            LOGGER.warn("Forcing shutdown of worker event loop...");
            workerGroup.shutdownGracefully(0L, 0L, TimeUnit.MILLISECONDS);
        }
        if (!bossGroup.isTerminated()) {
            LOGGER.warn("Forcing shutdown of boss event loop...");
            bossGroup.shutdownGracefully(0L, 0L, TimeUnit.MILLISECONDS);
        }
        MessageMetrics metrics = metricsCollector.computeMetrics();
        BytesMetrics bytesMetrics = bytesMetricsCollector.computeMetrics();
        LOGGER.info("Metrics messages[read={}, write={}] bytes[read={}, write={}]",
                metrics.messagesRead(), metrics.messagesWrote(), bytesMetrics.readBytes(), bytesMetrics.wroteBytes());
    }

}
