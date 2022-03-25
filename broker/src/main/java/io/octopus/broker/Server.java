package io.octopus.broker;

import io.handler.codec.mqtt.utils.HostUtils;
import io.octopus.Version;
import io.octopus.broker.handler.ReceiverMessageHandler;
import io.octopus.broker.router.RouteMessage2OtherBrokerServer;
import io.octopus.contants.BrokerConstants;
import io.octopus.broker.config.*;
import io.octopus.broker.handler.NewNettyMQTTHandler;
import io.octopus.broker.subscriptions.maptree.TopicMapSubscriptionDirectory;
import io.octopus.interception.InterceptHandler;
import io.octopus.persistence.RouterRegister;
import io.octopus.persistence.IQueueRepository;
import io.octopus.persistence.IRetainedRepository;
import io.octopus.broker.security.ISslContextCreator;
import io.octopus.persistence.ISubscriptionsRepository;
import io.octopus.persistence.h2.H2Builder;
import io.octopus.persistence.memory.MemoryQueueRepository;
import io.octopus.persistence.memory.MemoryRetainedRepository;
import io.octopus.persistence.memory.MemorySubscriptionsRepository;
import io.octopus.interception.BrokerNotifyInterceptor;
import io.octopus.broker.security.*;
import io.octopus.broker.subscriptions.ISubscriptionsDirectory;
import io.octopus.broker.security.IAuthenticator;
import io.octopus.broker.security.IAuthorizatorPolicy;
import io.handler.codec.mqtt.MqttPublishMessage;
import io.octopus.utils.ClassLoadUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.text.ParseException;
import java.util.*;
import java.util.concurrent.*;

import static io.octopus.utils.LoggingUtils.getInterceptorIds;

public class Server {

    private final Logger LOGGER = LoggerFactory.getLogger(this.getClass());
    private ScheduledExecutorService scheduler;
    private NewNettyAcceptor acceptor;
    private volatile boolean initialized;
    private PostOffice dispatcher;
    private BrokerNotifyInterceptor interceptor;
    private H2Builder h2Builder;
    private SessionRegistry sessions;
    private RouterRegister routerRegister;
    private RouteMessage2OtherBrokerServer routeMessage2OtherBrokerServer;
    private volatile static int listenerPort = BrokerConstants.udpTransportDefaultPort;


    public static void main(String[] args) throws IOException, InterruptedException {
        final Server server = new Server();
        server.startServer();
        //Bind a shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(server::stopServer));
    }

    /**
     * Starts Octopus bringing the configuration from the file located at m_config/Octopus.conf
     *
     * @throws IOException in case of any IO error.
     */
    public void startServer() throws IOException, InterruptedException {
        File defaultConfigurationFile = defaultConfigFile();
        LOGGER.info("Starting Octopus integration. Configuration file path={}", defaultConfigurationFile.getAbsolutePath());
        IResourceLoader filesystemLoader = new FileResourceLoader(defaultConfigurationFile);

//        String absolutePath = defaultConfigurationFile.getPath();
//        filesystemLoader = new ClasspathResourceLoader(absolutePath);

        final IConfig config = new ResourceLoaderConfig(filesystemLoader);
        startServer(config);
        LOGGER.info("{}  Server started, version: {}" ,Version.PROJECT_NAME,Version.VERSION);
    }

    private static File defaultConfigFile() {
        String configPath = System.getProperty("octopus.path", null);
//        if (null == configPath)
//            configPath = Thread.currentThread().getContextClassLoader().getResource("").getPath();
        return new File(configPath, IConfig.DEFAULT_CONFIG);
    }

    /**
     * Starts Octopus bringing the configuration from the given file
     *
     * @param configFile text file that contains the configuration.
     * @throws IOException in case of any IO Error.
     */
    public void startServer(File configFile) throws IOException, InterruptedException {
        LOGGER.info("Starting Octopus integration. Configuration file path: {}", configFile.getAbsolutePath());
        IResourceLoader filesystemLoader = new FileResourceLoader(configFile);
        final IConfig config = new ResourceLoaderConfig(filesystemLoader);
        startServer(config);
    }

    /**
     * Starts the integration with the given properties.
     * <p>
     * Its suggested to at least have the following properties:
     * <ul>
     * <li>port</li>
     * <li>password_file</li>
     * </ul>
     *
     * @param configProps the properties maptree to use as configuration.
     * @throws IOException in case of any IO Error.
     */
    public void startServer(Properties configProps) throws IOException, InterruptedException {
        LOGGER.debug("Starting Octopus integration using properties object");
        final IConfig config = new MemoryConfig(configProps);
        startServer(config);
    }

    /**
     * Starts Octopus bringing the configuration files from the given Config implementation.
     *
     * @param config the configuration to use to start the broker.
     * @throws IOException in case of any IO Error.
     */
    public void startServer(IConfig config) throws IOException, InterruptedException {
        LOGGER.debug("Starting Octopus integration using IConfig instance");
        listenerPort = config.getIntegerProperty("udp.port", BrokerConstants.udpTransportDefaultPort);
        startServer(config, null);
    }

    /**
     * Starts Octopus with config provided by an implementation of IConfig class and with the set
     * of InterceptHandler.
     *
     * @param config   the configuration to use to start the broker.
     * @param handlers the handlers to install in the broker.
     * @throws IOException in case of any IO Error.
     */
    public void startServer(IConfig config, List<? extends InterceptHandler> handlers) throws IOException, InterruptedException {
        LOGGER.debug("Starting Octopus integration using IConfig instance and intercept handlers");
//        clusterTopicRouter = new RedisRegister(config);
//        clusterTopicRouter = new ZookeeperRegisters(config);
//        routeMessage2OtherBrokerServer = new UdpDirectRouter(clusterTopicRouter);
        startServer(config, handlers, null, null, null);
    }

    public void startServer(IConfig config, List<? extends InterceptHandler> handlers, ISslContextCreator sslCtxCreator,
                            IAuthenticator authenticator, IAuthorizatorPolicy authorizatorPolicy) throws InterruptedException {
        final long start = System.currentTimeMillis();
        if (handlers == null) {
            handlers = Collections.emptyList();
        }
        LOGGER.trace("Starting Octopus Server. MQTT message interceptors={}", getInterceptorIds(handlers));

        scheduler = Executors.newScheduledThreadPool(1);

        final String handlerProp = System.getProperty(BrokerConstants.INTERCEPT_HANDLER_PROPERTY_NAME);
        if (handlerProp != null) {
            config.setProperty(BrokerConstants.INTERCEPT_HANDLER_PROPERTY_NAME, handlerProp);
        }
        final String persistencePath = config.getProperty(BrokerConstants.PERSISTENT_STORE_PROPERTY_NAME);
        LOGGER.debug("Configuring Using persistent store file, path: {}", persistencePath);
        initInterceptors(config, handlers);
        LOGGER.debug("Initialized MQTT protocol processor");
        if (sslCtxCreator == null) {
            LOGGER.info("Using default SSL context creator");
            sslCtxCreator = new DefaultOctopusSslContextCreator(config);
        }
        authenticator = initializeAuthenticator(authenticator, config);
        authorizatorPolicy = initializeAuthorizatorPolicy(authorizatorPolicy, config);

        final ISubscriptionsRepository subscriptionsRepository;
        final IQueueRepository queueRepository;
        final IRetainedRepository retainedRepository;

        /**
         * 持久化位置，如果没有配置就进else，走内存存储
         */
        if (persistencePath != null && !persistencePath.isEmpty()) {
            LOGGER.info("Configuring H2 subscriptions store to {}", persistencePath);
            h2Builder = new H2Builder(config, scheduler).initStore();
//            subscriptionsRepository = h2Builder.subscriptionsRepository();
            subscriptionsRepository = new MemorySubscriptionsRepository();
            queueRepository = h2Builder.queueRepository();
            retainedRepository = h2Builder.retainedRepository();
        } else {
            LOGGER.info("Configuring in-memory subscriptions store");
            subscriptionsRepository = new MemorySubscriptionsRepository();
            queueRepository = new MemoryQueueRepository();
            retainedRepository = new MemoryRetainedRepository();
        }

        //这里切换存储方式
//        ISubscriptionsDirectory subscriptions = new CTrieSubscriptionDirectory();

        ISubscriptionsDirectory subscriptions = new TopicMapSubscriptionDirectory();
        subscriptions.init(subscriptionsRepository);
        final Authorizator authorizator = new Authorizator(authorizatorPolicy);
        sessions = new SessionRegistry(subscriptions, queueRepository, authorizator);
        dispatcher = new PostOffice(subscriptions, retainedRepository, sessions, interceptor, authorizator);

        String registerUser = config.getProperty(BrokerConstants.REGISTER_CENTER_USER, null);
        if (null != registerUser)
            dispatcher.addRegisterUserName(registerUser.split(","));

        final BrokerConfiguration brokerConfig = new BrokerConfiguration(config);
        MqttConnectionFactory connectionFactory = new MqttConnectionFactory(brokerConfig, authenticator, sessions,
                dispatcher, interceptor);

        final NewNettyMQTTHandler mqttHandler = new NewNettyMQTTHandler(connectionFactory);
        acceptor = new NewNettyAcceptor(brokerConfig,new ReceiverMessageHandler(dispatcher));
        acceptor.initialize(mqttHandler,config, sslCtxCreator, dispatcher, sessions,subscriptions);

        final long startTime = System.currentTimeMillis() - start;
        LOGGER.info("Octopus integration has been started successfully in {} ms", startTime);
        initialized = true;
    }

    private IAuthorizatorPolicy initializeAuthorizatorPolicy(IAuthorizatorPolicy authorizatorPolicy, IConfig props) {
        LOGGER.debug("Configuring MQTT authorizator policy");
        String authorizatorClassName = props.getProperty(BrokerConstants.AUTHORIZATOR_CLASS_NAME, "");
        if (authorizatorPolicy == null && !authorizatorClassName.isEmpty()) {
            authorizatorPolicy = ClassLoadUtils.loadClass(this.getClass().getClassLoader(),
                    authorizatorClassName, IAuthorizatorPolicy.class, IConfig.class, props);
        }

        if (authorizatorPolicy == null) {
            String aclFilePath = props.getProperty(BrokerConstants.ACL_FILE_PROPERTY_NAME, "");
            if (aclFilePath != null && !aclFilePath.isEmpty()) {
                authorizatorPolicy = new DenyAllAuthorizatorPolicy();
                try {
                    LOGGER.info("Parsing ACL file. Path = {}", aclFilePath);
                    IResourceLoader resourceLoader = props.getResourceLoader();
                    authorizatorPolicy = ACLFileParser.parse(resourceLoader.loadResource(aclFilePath));
                } catch (ParseException pex) {
                    LOGGER.error("Unable to parse ACL file. path = {}", aclFilePath, pex);
                }
            } else {
                authorizatorPolicy = new PermitAllAuthorizatorPolicy();
            }
            LOGGER.info("Authorizator policy {} instance will be used", authorizatorPolicy.getClass().getName());
        }
        return authorizatorPolicy;
    }

    private IAuthenticator initializeAuthenticator(IAuthenticator authenticator, IConfig props) {
        LOGGER.debug("Configuring MQTT authenticator");
        String authenticatorClassName = props.getProperty(BrokerConstants.AUTHENTICATOR_CLASS_NAME, "");

        if (authenticator == null && !authenticatorClassName.isEmpty()) {
            authenticator = ClassLoadUtils.loadClass(this.getClass().getClassLoader(),
                    authenticatorClassName, IAuthenticator.class, IConfig.class, props);
        }

        IResourceLoader resourceLoader = props.getResourceLoader();
        if (authenticator == null) {
            String passwdPath = props.getProperty(BrokerConstants.PASSWORD_FILE_PROPERTY_NAME, "");

            if (passwdPath.isEmpty()) {
                authenticator = new AcceptAllAuthenticator();
            } else {
                authenticator = new ResourceAuthenticator(resourceLoader, passwdPath);
            }
            LOGGER.info("An {} authenticator instance will be used", authenticator.getClass().getName());
        }
        return authenticator;
    }

    /**
     * init InterceptHandlerListener
     *
     * @param props             config
     * @param embeddedObservers listener
     */
    private void initInterceptors(IConfig props, List<? extends InterceptHandler> embeddedObservers) {
        LOGGER.info("Configuring message interceptors...");

        List<InterceptHandler> observers = new ArrayList<>(embeddedObservers);
        String interceptorClassName = props.getProperty(BrokerConstants.INTERCEPT_HANDLER_PROPERTY_NAME);
        if (interceptorClassName != null && !interceptorClassName.isEmpty()) {
            InterceptHandler handler = ClassLoadUtils.loadClass(this.getClass().getClassLoader(),
                    interceptorClassName, InterceptHandler.class,
                    io.octopus.broker.Server.class, this);
            if (handler != null) {
                observers.add(handler);
            }
        }
        interceptor = new BrokerNotifyInterceptor(props, observers);
    }


    /**
     * Use the broker to publish a message. It's intended for embedding applications. It can be used
     * only after the integration is correctly started with startServer.
     *
     * @param msg      the message to forward.
     * @param clientId the id of the sending integration.
     * @throws IllegalStateException if the integration is not yet started
     */
    public void internalPublish(MqttPublishMessage msg, final String clientId) {
        final int messageID = msg.variableHeader().packetId();
        if (!initialized) {
            LOGGER.error("Octopus is not started, internal message cannot be published. CId: {}, messageId: {}", clientId,
                    messageID);
            throw new IllegalStateException("Can't publish on a integration is not yet started");
        }
        LOGGER.trace("Internal publishing message CId: {}, messageId: {}", clientId, messageID);
        dispatcher.internalPublish(msg);
        msg.payload().release();
    }

    public void stopServer() {
        LOGGER.info("Unbinding integration from the configured ports");
        acceptor.close();
        LOGGER.trace("Stopping MQTT protocol processor");
        initialized = false;

        // calling shutdown() does not actually stop tasks that are not cancelled,
        // and SessionsRepository does not stop its tasks. Thus shutdownNow().
        scheduler.shutdownNow();

        if (h2Builder != null) {
            LOGGER.trace("Shutting down H2 persistence {}",h2Builder);
            h2Builder.closeStore();
        }

        RouterRegister routerRegister = getClusterTopicRouter();
        if (null != routerRegister)
            routerRegister.remove(new InetSocketAddress(HostUtils.getPath(), listenerPort));

        interceptor.stop();
        LOGGER.info("Octopus integration has been stopped.");
    }

    public int getPort() {
        return acceptor.getPort();
    }

    public int getSslPort() {
        return acceptor.getSslPort();
    }

    /**
     * SPI method used by Broker embedded applications to get list of subscribers. Returns null if
     * the broker is not started.
     *
     * @return list of subscriptions.
     */
// TODO reimplement this
//    public List<Subscription> getSubscriptions() {
//        if (m_processorBootstrapper == null) {
//            return null;
//        }
//        return this.subscriptionsStore.listAllSubscriptions();
//    }

    /**
     * SPI method used by Broker embedded applications to add intercept handlers.
     *
     * @param interceptHandler the handler to add.
     */
    public void addInterceptHandler(InterceptHandler interceptHandler) {
        if (!initialized) {
            LOGGER.error("Octopus is not started, MQTT message interceptor cannot be added. InterceptorId={}",
                    interceptHandler.getID());
            throw new IllegalStateException("Can't register interceptors on a integration that is not yet started");
        }
        LOGGER.info("Adding MQTT message interceptor. InterceptorId={}", interceptHandler.getID());
        interceptor.addInterceptHandler(interceptHandler);
    }

    /**
     * SPI method used by Broker embedded applications to remove intercept handlers.
     *
     * @param interceptHandler the handler to remove.
     */
    public void removeInterceptHandler(InterceptHandler interceptHandler) {
        if (!initialized) {
            LOGGER.error("Octopus is not started, MQTT message interceptor cannot be removed. InterceptorId={}",
                    interceptHandler.getID());
            throw new IllegalStateException("Can't deregister interceptors from a integration that is not yet started");
        }
        LOGGER.info("Removing MQTT message interceptor. InterceptorId={}", interceptHandler.getID());
        interceptor.removeInterceptHandler(interceptHandler);
    }

    /**
     * Return a list of descriptors of connected clients.
     */
    public Collection<ClientDescriptor> listConnectedClients() {
        return sessions.listConnectedClients();
    }

    public RouterRegister getClusterTopicRouter() {
        return routerRegister;
    }

    public RouteMessage2OtherBrokerServer getRouteMessage2OtherBrokerServer() {
        return routeMessage2OtherBrokerServer;
    }

}
