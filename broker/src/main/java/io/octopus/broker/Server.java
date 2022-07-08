//package io.octopus.broker;
//
//import io.octopus.base.message.IMessage;
//import io.handler.codec.mqtt.MqttPublishMessage;
//import io.octopus.base.utils.HostUtils;
//import io.netty.util.concurrent.DefaultThreadFactory;
//import io.octopus.Version;
//import io.octopus.base.interfaces.*;
//import io.octopus.broker.security.*;
//import io.octopus.broker.session.MqttConnectionFactory;
//import io.octopus.broker.session.SessionRegsistor;
//import io.octopus.base.config.*;
//import io.octopus.base.contants.BrokerConstants;
//import io.octopus.interception.BrokerNotifyInterceptor;
//import io.octopus.interception.InterceptHandler;
//import io.octopus.scala.mqtt.persistence.ThreadQueue;
//import io.octopus.base.queue.MsgQueue;
//import io.octopus.broker.handler.NettyMQTTHandler;
//import io.octopus.broker.handler.ReceiverMessageHandler;
//import io.octopus.router.RouteMessage2OtherBrokerServer;
//import io.octopus.base.utils.ClassLoadUtils;
//import io.store.persistence.disk.CheckPointServer;
//import io.store.persistence.flishdisk.FlushDiskServer;
//import io.store.persistence.h2.H2Builder;
//import io.store.persistence.memory.MemoryQueueRepository;
//import io.store.persistence.memory.MemoryRetainedRepository;
//import io.store.persistence.memory.MemorySubscriptionsRepository;
//import io.octopus.base.subscriptions.ISubscriptionsDirectory;
//import io.store.persistence.maptree.TopicMapSubscriptionDirectory;
//import org.h2.mvstore.MVStore;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//
//import java.io.File;
//import java.io.IOException;
//import java.net.InetSocketAddress;
//import java.text.ParseException;
//import java.util.*;
//import java.util.concurrent.Executors;
//import java.util.concurrent.ScheduledExecutorService;
//
//import static io.octopus.base.utils.LoggingUtils.getInterceptorIds;
//
//public class Server {
//
//    private final Logger logger = LoggerFactory.getLogger(this.getClass());
//
//    private NettyAcceptor acceptor;
//    private volatile boolean initialized;
//    private MsgDispatcher msgDispatcher;
//    private BrokerNotifyInterceptor interceptor;
//    private H2Builder h2Builder;
//    private SessionRegsistor sessionRegistry;
//    private IRouterRegister routerRegister;
//    private RouteMessage2OtherBrokerServer routeMessage2OtherBrokerServer;
//    private volatile static int listenerPort = BrokerConstants.UDP_TRANSPORT_DEFAULT_PORT;
//    private MsgQueue<IMessage> msgQueue;
//    private ScheduledExecutorService dispatchMsgService;
//    private ScheduledExecutorService flushDiskService;
//    private CheckPointServer checkPointServer;
//    private MVStore mvStore;
//
//
//    public static void main(String[] args) throws IOException, InterruptedException {
//        final Server server = new Server();
//        server.startServer();
//        //Bind a shutdown hook
//        Runtime.getRuntime().addShutdownHook(new Thread(server::stopServer));
//    }
//
//    /**
//     * Starts Octopus bringing the configuration from the file located config/Octopus.conf
//     *
//     * @throws IOException in case of any IO error.
//     */
//    public void startServer() throws IOException, InterruptedException {
//        File defaultConfigurationFile = defaultConfigFile();
//        logger.info("Starting Octopus integration. Configuration file path={}", defaultConfigurationFile.getAbsolutePath());
//        IResourceLoader filesystemLoader = new FileResourceLoader(defaultConfigurationFile);
//        final IConfig config = new ResourceLoaderConfig(filesystemLoader);
//        startServer(config);
//        logger.info("{}  Server started, version: {}", Version.PROJECT_NAME, Version.VERSION);
//    }
//
//    private static File defaultConfigFile() {
//        //file System
//        String configPath = System.getProperty("octopus.path", null);
//        return new File(configPath, IConfig.DEFAULT_CONFIG);
//    }
//
//    /**
//     * Starts Octopus bringing the configuration from the given file
//     *
//     * @param configFile text file that contains the configuration.
//     * @throws IOException in case of any IO Error.
//     */
//    public void startServer(File configFile) throws IOException, InterruptedException {
//        logger.info("Starting Octopus integration. Configuration file path: {}", configFile.getAbsolutePath());
//        IResourceLoader filesystemLoader = new FileResourceLoader(configFile);
//        final IConfig config = new ResourceLoaderConfig(filesystemLoader);
//        startServer(config);
//    }
//
//    /**
//     * Starts the integration with the given properties.
//     * <p>
//     * Its suggested to at least have the following properties:
//     * <ul>
//     * <li>port</li>
//     * <li>password_file</li>
//     * </ul>
//     *
//     * @param configProps the properties maptree to use as configuration.
//     * @throws IOException in case of any IO Error.
//     */
//    public void startServer(Properties configProps) throws IOException, InterruptedException {
//        logger.debug("Starting Octopus integration using properties object");
//        final IConfig config = new MemoryConfig(configProps);
//        startServer(config);
//    }
//
//    /**
//     * Starts Octopus bringing the configuration files from the given Config implementation.
//     *
//     * @param config the configuration to use to start the broker.
//     * @throws IOException in case of any IO Error.
//     */
//    public void startServer(IConfig config) throws IOException, InterruptedException {
//        logger.debug("Starting Octopus integration using IConfig instance");
//        listenerPort = config.getIntegerProperty("udp.port", BrokerConstants.UDP_TRANSPORT_DEFAULT_PORT);
//        startServer(config, null);
//    }
//
//    /**
//     * Starts Octopus with config provided by an implementation of IConfig class and with the set
//     * of InterceptHandler.
//     *
//     * @param config   the configuration to use to start the broker.
//     * @param handlers the handlers to install in the broker.
//     * @throws IOException in case of any IO Error.
//     */
//    public void startServer(IConfig config, List<? extends InterceptHandler> handlers) throws IOException, InterruptedException {
//        logger.debug("Starting Octopus integration using IConfig instance and intercept handlers");
////        clusterTopicRouter = new RedisRegister(config);
////        clusterTopicRouter = new ZookeeperRegisters(config);
////        routeMessage2OtherBrokerServer = new UdpDirectRouter(clusterTopicRouter);
//        startServer(config, handlers, null, null, null);
//    }
//
//    public void startServer(IConfig config, List<? extends InterceptHandler> handlers, ISslContextCreator sslCtxCreator,
//                            IAuthenticator authenticator, IRWController authorizatorPolicy) throws InterruptedException, IOException {
//        final long start = System.currentTimeMillis();
//        if (handlers == null) {
//            handlers = Collections.emptyList();
//        }
//        logger.trace("Starting Octopus Server. MQTT message interceptors={}", getInterceptorIds(handlers));
//
//        flushDiskService = Executors.newSingleThreadScheduledExecutor(new DefaultThreadFactory("flush-disk-server"));
//        final String handlerProp = System.getProperty(BrokerConstants.INTERCEPT_HANDLER_PROPERTY_NAME);
//        if (handlerProp != null) {
//            config.setProperty(BrokerConstants.INTERCEPT_HANDLER_PROPERTY_NAME, handlerProp);
//        }
//         String persistencePath = config.getProperty(BrokerConstants.PERSISTENT_STORE_PROPERTY_NAME);
//        logger.debug("Configuring Using persistent store file, path: {}", persistencePath);
//        initInterceptors(config, handlers);
//        logger.debug("Initialized MQTT protocol processor");
//        if (sslCtxCreator == null) {
//            logger.info("Using default SSL context creator");
//            sslCtxCreator = new DefaultOctopusSslContextCreator(config);
//        }
//        authenticator = initializeAuthenticator(authenticator, config);
//        authorizatorPolicy = initializePermissionControl(authorizatorPolicy, config);
//
//        final ISubscriptionsRepository subscriptionsRepository;
//        final IQueueRepository queueRepository;
//        final IRetainedRepository retainedRepository;
//
//        /**
//         * 持久化位置，如果没有配置就进else，走内存存储
//         */
//        if (persistencePath != null && !persistencePath.isEmpty()) {
//            logger.info("Configuring H2 subscriptions store to {}", persistencePath);
//            persistencePath = persistencePath+"h2/octopus_store.db";
//            initPersistencePath(persistencePath);
//            mvStore = new MVStore.Builder()
//                    .fileName(persistencePath)
//                    .autoCommitDisabled()
//                    .open();
//            h2Builder = new H2Builder(config).initStore(mvStore);
////            subscriptionsRepository = h2Builder.subscriptionsRepository();
//            subscriptionsRepository = new MemorySubscriptionsRepository();
//            queueRepository = h2Builder.queueRepository();
//            retainedRepository = h2Builder.retainedRepository();
//        } else {
//            logger.info("Configuring in-memory subscriptions store");
//            subscriptionsRepository = new MemorySubscriptionsRepository();
//            queueRepository = new MemoryQueueRepository();
//            retainedRepository = new MemoryRetainedRepository();
//        }
//
//        //这里切换存储方式
////        ISubscriptionsDirectory subscriptions = new CTrieSubscriptionDirectory();
//
//        ISubscriptionsDirectory subscriptions = new TopicMapSubscriptionDirectory();
//        subscriptions.init(subscriptionsRepository);
//        final ReadWriteControl readWriteControl = new ReadWriteControl(authorizatorPolicy);
//        checkPointServer = new CheckPointServer();
////        msgQueue = new ConcurrentFileQueue(null, checkPointServer);
//        msgQueue = new ThreadQueue(config,mvStore,checkPointServer,flushDiskService);
//        sessionRegistry = new SessionRegsistor(subscriptions, queueRepository, readWriteControl, msgQueue);
//        msgDispatcher = new MsgDispatcher(subscriptions, retainedRepository, sessionRegistry, interceptor, readWriteControl);
//
//        //init queue
//        msgQueue.initialize(msgDispatcher);
//
//        String registerUser = config.getProperty(BrokerConstants.REGISTER_CENTER_USER, null);
//        if (null != registerUser)
//            msgDispatcher.addRegisterUserName(registerUser.split(","));
//
//        final BrokerConfiguration brokerConfig = new BrokerConfiguration(config);
//        MqttConnectionFactory connectionFactory = new MqttConnectionFactory(brokerConfig, authenticator, sessionRegistry,
//                msgDispatcher, interceptor, readWriteControl, msgQueue);
//
//        final NettyMQTTHandler mqttHandler = new NettyMQTTHandler(connectionFactory);
//        acceptor = new NettyAcceptor(brokerConfig, new ReceiverMessageHandler(msgDispatcher));
//        acceptor.initialize(mqttHandler, config, sslCtxCreator, msgDispatcher, sessionRegistry, subscriptions);
////        initDispatchMsgServiceService();
////        initFlushDiskService(config);
//        final long startTime = System.currentTimeMillis() - start;
//        logger.info("Octopus integration has been started successfully in {} ms", startTime);
//        initialized = true;
//    }
//
//    private void initPersistencePath(String persistencePath) {
//        logger.info("Initializing H2 store");
//        if (persistencePath == null || persistencePath.isEmpty()) {
//            throw new IllegalArgumentException("H2 store path can't be null or empty");
//        }
//        File directory = new File(persistencePath);
//        if (!directory.getParentFile().exists()) {
//            directory.getParentFile().mkdirs();
//        }
//    }
//
//    /**
//     * init flushDisk server
//     *
//     * @param config config
//     */
//    private void initFlushDiskService(IConfig config) {
//        FlushDiskServer flushDiskServer = new FlushDiskServer(msgQueue, mvStore, config, flushDiskService, checkPointServer);
//        flushDiskServer.start();
//    }
//
////    /**
////     * init dispatchMsgServer
////     */
////    private void initDispatchMsgServiceService() {
////        dispatchMsgService = Executors.newSingleThreadScheduledExecutor(new DefaultThreadFactory("dispatchMsgService"));
////        dispatchMsgService.scheduleWithFixedDelay(new PullMessageWorker(msgDispatcher, msgQueue),
////                2, 2, TimeUnit.SECONDS);
////    }
//
//    private IRWController initializePermissionControl(IRWController authorityController, IConfig props) {
//        logger.debug("Configuring MQTT authorizator policy");
//        String authorizatorClassName = props.getProperty(BrokerConstants.AUTHORIZATOR_CLASS_NAME, "");
//        if (authorityController == null && !authorizatorClassName.isEmpty()) {
//            authorityController = ClassLoadUtils.loadClass(this.getClass().getClassLoader(),
//                    authorizatorClassName, IRWController.class, IConfig.class, props);
//        }
//
//        if (authorityController == null) {
//            String aclFilePath = props.getProperty(BrokerConstants.ACL_FILE_PROPERTY_NAME, "");
//            if (aclFilePath != null && !aclFilePath.isEmpty()) {
//                authorityController = new DenyAllAuthorityController();
//                try {
//                    logger.info("Parsing ACL file. Path = {}", aclFilePath);
//                    IResourceLoader resourceLoader = props.getResourceLoader();
//                    authorityController = ACLFileParser.parse(resourceLoader.loadResource(aclFilePath));
//                } catch (ParseException pex) {
//                    logger.error("Unable to parse ACL file. path = {}", aclFilePath, pex);
//                }
//            } else {
//                authorityController = new PermitAllAuthorityController();
//            }
//            logger.info("Authorizator policy {} instance will be used", authorityController.getClass().getName());
//        }
//        return authorityController;
//    }
//
//    private IAuthenticator initializeAuthenticator(IAuthenticator authenticator, IConfig props) {
//        logger.debug("Configuring MQTT authenticator");
//        String authenticatorClassName = props.getProperty(BrokerConstants.AUTHENTICATOR_CLASS_NAME, "");
//
//        if (authenticator == null && !authenticatorClassName.isEmpty()) {
//            authenticator = ClassLoadUtils.loadClass(this.getClass().getClassLoader(),
//                    authenticatorClassName, IAuthenticator.class, IConfig.class, props);
//        }
//
//        IResourceLoader resourceLoader = props.getResourceLoader();
//        if (authenticator == null) {
//            String passwdPath = props.getProperty(BrokerConstants.PASSWORD_FILE_PROPERTY_NAME, "");
//
//            if (passwdPath.isEmpty()) {
//                authenticator = new AcceptAllAuthenticator();
//            } else {
//                authenticator = new ResourceAuthenticator(resourceLoader, passwdPath);
//            }
//            logger.info("An {} authenticator instance will be used", authenticator.getClass().getName());
//        }
//        return authenticator;
//    }
//
//    /**
//     * init InterceptHandlerListener
//     *
//     * @param props             config
//     * @param embeddedObservers listener
//     */
//    private void initInterceptors(IConfig props, List<? extends InterceptHandler> embeddedObservers) {
//        logger.info("Configuring message interceptors...");
//
//        List<InterceptHandler> observers = new ArrayList<>(embeddedObservers);
//        String interceptorClassName = props.getProperty(BrokerConstants.INTERCEPT_HANDLER_PROPERTY_NAME);
//        if (interceptorClassName != null && !interceptorClassName.isEmpty()) {
//            InterceptHandler handler = ClassLoadUtils.loadClass(this.getClass().getClassLoader(),
//                    interceptorClassName, InterceptHandler.class,
//                    io.octopus.broker.Server.class, this);
//            if (handler != null) {
//                observers.add(handler);
//            }
//        }
//        interceptor = new BrokerNotifyInterceptor(props, observers);
//    }
//
//
//    /**
//     * Use the broker to publish a message. It's intended for embedding applications. It can be used
//     * only after the integration is correctly started with startServer.
//     *
//     * @param msg      the message to forward.
//     * @param clientId the id of the sending integration.
//     * @throws IllegalStateException if the integration is not yet started
//     */
//    public void internalPublish(MqttPublishMessage msg, final String clientId) {
//        final int messageID = msg.variableHeader().packetId();
//        if (!initialized) {
//            logger.error("Octopus is not started, internal message cannot be published. CId: {}, messageId: {}", clientId,
//                    messageID);
//            throw new IllegalStateException("Can't publish on a integration is not yet started");
//        }
//        logger.trace("Internal publishing message CId: {}, messageId: {}", clientId, messageID);
//        msgDispatcher.internalPublish(msg);
//        msg.payload().release();
//    }
//
//    public void stopServer() {
//        logger.info("Unbinding integration from the configured ports");
//        acceptor.close();
//        logger.trace("Stopping MQTT protocol processor");
//        initialized = false;
//
//        // calling shutdown() does not actually stop tasks that are not cancelled,
//        // and SessionsRepository does not stop its tasks. Thus shutdownNow().
//        flushDiskService.shutdownNow();
//        dispatchMsgService.shutdownNow();
//
//        if (h2Builder != null) {
//            logger.trace("Shutting down H2 persistence {}", h2Builder);
//            h2Builder.closeStore();
//        }
//
//        IRouterRegister routerRegister = getClusterTopicRouter();
//        if (null != routerRegister)
//            routerRegister.remove(new InetSocketAddress(Objects.requireNonNull(HostUtils.getPath()), listenerPort));
//
//        interceptor.stop();
//        logger.info("Octopus integration has been stopped.");
//    }
//
//    public int getPort() {
//        return acceptor.getPort();
//    }
//
//    public int getSslPort() {
//        return acceptor.getSslPort();
//    }
//
//    /**
//     * SPI method used by Broker embedded applications to get list of subscribers. Returns null if
//     * the broker is not started.
//     *
//     * @return list of subscriptions.
//     */
//// TODO reimplement this
////    public List<Subscription> getSubscriptions() {
////        if (m_processorBootstrapper == null) {
////            return null;
////        }
////        return this.subscriptionsStore.listAllSubscriptions();
////    }
//
//    /**
//     * SPI method used by Broker embedded applications to add intercept handlers.
//     *
//     * @param interceptHandler the handler to add.
//     */
//    public void addInterceptHandler(InterceptHandler interceptHandler) {
//        if (!initialized) {
//            logger.error("Octopus is not started, MQTT message interceptor cannot be added. InterceptorId={}",
//                    interceptHandler.getID());
//            throw new IllegalStateException("Can't register interceptors on a integration that is not yet started");
//        }
//        logger.info("Adding MQTT message interceptor. InterceptorId={}", interceptHandler.getID());
//        interceptor.addInterceptHandler(interceptHandler);
//    }
//
//    /**
//     * SPI method used by Broker embedded applications to remove intercept handlers.
//     *
//     * @param interceptHandler the handler to remove.
//     */
//    public void removeInterceptHandler(InterceptHandler interceptHandler) {
//        if (!initialized) {
//            logger.error("Octopus is not started, MQTT message interceptor cannot be removed. InterceptorId={}",
//                    interceptHandler.getID());
//            throw new IllegalStateException("Can't deregister interceptors from a integration that is not yet started");
//        }
//        logger.info("Removing MQTT message interceptor. InterceptorId={}", interceptHandler.getID());
//        interceptor.removeInterceptHandler(interceptHandler);
//    }
//
//    /**
//     * Return a list of descriptors of connected clients.
//     */
//    public Collection<ClientDescriptor> listConnectedClients() {
//        return sessionRegistry.listConnectedClients();
//    }
//
//    public IRouterRegister getClusterTopicRouter() {
//        return routerRegister;
//    }
//
//    public RouteMessage2OtherBrokerServer getRouteMessage2OtherBrokerServer() {
//        return routeMessage2OtherBrokerServer;
//    }
//
//}
