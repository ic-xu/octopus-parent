package com.octopus.starter;


import io.octopus.kernel.Version;
import io.octopus.kernel.kernel.*;
import io.octopus.kernel.config.FileResourceLoader;
import io.octopus.kernel.config.IConfig;
import io.octopus.kernel.config.IResourceLoader;
import io.octopus.kernel.config.ResourceLoaderConfig;
import io.octopus.kernel.contants.BrokerConstants;
import io.octopus.kernel.kernel.interceptor.ConnectionNotifyInterceptor;
import io.octopus.kernel.kernel.interceptor.PostOfficeNotifyInterceptor;
import io.octopus.kernel.kernel.listener.LifecycleListener;
import io.octopus.kernel.kernel.repository.*;
import io.octopus.kernel.kernel.security.*;
import io.octopus.kernel.kernel.subscriptions.ISubscriptionsDirectory;
import io.octopus.kernel.utils.ClassLoadUtils;
import io.octopus.kernel.utils.ObjectUtils;
import io.store.persistence.StoreCreateFactory;
import io.store.persistence.disk.CheckPointServer;
import io.store.persistence.memory.TopicMapSubscriptionDirectory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author chenxu
 * @version 1
 * @date 2022/7/14 10:50
 */
public class ServerStarter {

    private final static Logger LOGGER = LoggerFactory.getLogger(ServerStarter.class);

    /**
     * 用来存储需要启动的服务列表
     */
    private final List<IServer> serverList = new ArrayList<>();


    /**
     * 添加链接消息拦截器
     */
    private final List<ConnectionNotifyInterceptor> connectionInterceptors = new ArrayList<>();

    /**
     * 消息分发拦截器
     */
    private final List<PostOfficeNotifyInterceptor> postOfficeNotifyInterceptors = new ArrayList<>();

    private IConfig config;


    private IAuthenticator authenticator;
    private IRWController irwController;
    private IStoreCreateFactory storeFactory;


    public ServerStarter() {
        this(null);
    }

    public ServerStarter(List<IServer> initServer) {
        this(initServer, null);
    }


    public ServerStarter(List<IServer> initServer, List<ConnectionNotifyInterceptor> interceptors) {
        if (!ObjectUtils.isEmpty(initServer)) {
            serverList.addAll(initServer);
        }

        if (!ObjectUtils.isEmpty(initServer)) {
            connectionInterceptors.addAll(interceptors);
        }
    }


    public void addServer(IServer server) {
        if (!ObjectUtils.isEmpty(server)) {
            serverList.add(server);
        }
    }

    /**
     * start the server whit create config
     */
    public void startServer() {
        long startTime = System.currentTimeMillis();
        IConfig config = createDefaultConfig();
        startServer(config);
        Long startServerTime = System.currentTimeMillis() - startTime;
        Runtime.getRuntime().addShutdownHook(new Thread(this::close));
        LOGGER.info("{} ,version {} ,build package time {} has been started successfully in {} ms",
                Version.PROJECT_NAME, Version.VERSION, Version.TIMESTAMP, startServerTime);
    }


    /**
     * start the server Lifecycle
     *
     * @param config c
     */
    public void startServer(IConfig config) {
        this.config = config;
        try {
            config.printEnvironment();
            initLifecycle();
            initPostOfficeInterceptor();
            initializeAuthenticator();
            initializePermissionControl();
            initializeStore();
            initAndStartNettyAcepter();
            startLifecycle();
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }


    /**
     * 初始化消息分发拦截器
     */
    private void initPostOfficeInterceptor() {
        LOGGER.info("Configuring message interceptors...");
        List<String> interceptorClassNameList = new ArrayList<>();
        String handlerProp = System.getProperty(BrokerConstants.INTERCEPT_HANDLER_PROPERTY_NAME);
        if (!ObjectUtils.isEmpty(handlerProp)) {
            interceptorClassNameList.add(handlerProp);
        }


        String interceptorClassName = config.getProperty(BrokerConstants.INTERCEPT_HANDLER_PROPERTY_NAME);
        if (!ObjectUtils.isEmpty(interceptorClassName)) {
            interceptorClassNameList.add(interceptorClassName);
        }

        for (String classNameString : interceptorClassNameList) {
            try {
                PostOfficeNotifyInterceptor interceptor = ClassLoadUtils.loadClass(this.getClass().getClassLoader(),
                        classNameString, PostOfficeNotifyInterceptor.class, ServerStarter.class, this);
                postOfficeNotifyInterceptors.add(interceptor);
            } catch (Exception e) {
                LOGGER.error(e.getMessage());
            }
        }
    }

    /**
     * 初始化认证器
     */
    private void initializeAuthenticator() {
        LOGGER.debug("Configuring  authenticator");
        String authenticatorClassName = config.getProperty(BrokerConstants.AUTHENTICATOR_CLASS_NAME, "");
        if (ObjectUtils.isEmpty(authenticator) && !authenticatorClassName.isEmpty()) {
            this.authenticator = ClassLoadUtils.loadClass(this.getClass().getClassLoader(), authenticatorClassName, IAuthenticator.class, IConfig.class, config);
        }
        IResourceLoader resourceLoader = config.getResourceLoader();
        if (authenticator == null) {
            String passwdPath = config.getProperty(BrokerConstants.PASSWORD_FILE_PROPERTY_NAME, "");
            if (passwdPath.isEmpty()) {
                this.authenticator = new AcceptAllAuthenticator();
            } else {
                this.authenticator = new ResourceAuthenticator(resourceLoader, passwdPath);
            }
            LOGGER.info("An {} authenticator instance will be used", this.authenticator.getClass().getName());
        }
    }

    /**
     * 初始化权限控制
     */
    private void initializePermissionControl() {

        String authorizatorClassName = config.getProperty(BrokerConstants.AUTHORIZATOR_CLASS_NAME, "");
        if (irwController == null && !authorizatorClassName.isEmpty()) {
            this.irwController = ClassLoadUtils.loadClass(this.getClass().getClassLoader(), authorizatorClassName, IRWController.class, IConfig.class, config);
        }

        if (irwController == null) {
            String aclFilePath = config.getProperty(BrokerConstants.ACL_FILE_PROPERTY_NAME, "");
            if (aclFilePath != null && !aclFilePath.isEmpty()) {
                this.irwController = new DenyAllAuthorityController();
                try {
                    LOGGER.info("Parsing ACL file. Path = {}", aclFilePath);
                    IResourceLoader resourceLoader = config.getResourceLoader();
                    this.irwController = ACLFileParser.parse(resourceLoader.loadResource(aclFilePath));
                } catch (ParseException e) {
                    e.printStackTrace();
                    LOGGER.error("Unable to parse ACL file. path = {} , err msg {}", aclFilePath, e.getMessage());
                }
            } else {
                this.irwController = new PermitAllAuthorityController();
            }
            LOGGER.info("Authorizator policy {} instance will be used", this.irwController.getClass().getName());
        }
    }


    /**
     * 初始化存储管理器
     */
    private void initializeStore() throws IOException {
        storeFactory = new StoreCreateFactory(config);
    }


    /**
     * 真正运行服务逻辑
     */
    private void initAndStartNettyAcepter() throws Exception {

        ISubscriptionsRepository subscriptionsRepository = storeFactory.createISubscriptionsRepository();
        ISubscriptionsDirectory subscriptions = new TopicMapSubscriptionDirectory();
        subscriptions.init(subscriptionsRepository);


        IndexQueueFactory indexQueueFactory = storeFactory.createIndexQueueRepository();
        ReadWriteControl readWriteControl = new ReadWriteControl(this.irwController);
//        CheckPointServer checkPointServer = new CheckPointServer();
//        DefaultSessionResistor sessionResistor = new DefaultSessionResistor(queueRepository, readWriteControl, config, new MemoryRepository(config, checkPointServer));
        DefaultSessionResistor sessionResistor = new DefaultSessionResistor(indexQueueFactory, readWriteControl, config, storeFactory.createIMsgQueueRepository());


        IRetainedRepository retainedRepository = storeFactory.createIRetainedRepository();
        IMsgQueue iMsgQueue = storeFactory.createIMsgQueueRepository();
        IPostOffice postOffice = new DefaultPostOffice(iMsgQueue,subscriptions, retainedRepository, sessionResistor, this.postOfficeNotifyInterceptors, readWriteControl);
        sessionResistor.setPostOffice(postOffice);

        //    this.sessionResistor = new SessionRegistry(subscriptions, queueRepository, authorizator, msgQueue)
        //    this.postOffice = new MsgDispatcher(subscriptions, retainedRepository, sessionResistor, interceptor, authorizator)
        String registerUser = config.getProperty(BrokerConstants.REGISTER_CENTER_USER, null);
        if (null != registerUser) {
            postOffice.addAdminUser(registerUser.split(","));
        }

        TransportBootstrap acceptor = new TransportBootstrap(authenticator, connectionInterceptors, readWriteControl);
        acceptor.initialize(config, postOffice, sessionResistor, subscriptions);


    }

    /**
     * 初始化服务
     *
     * @throws Exception e
     */
    private void initLifecycle() throws Exception {
        for (IServer server : serverList) {
            if (ObjectUtils.isEmpty(server)) {
                continue;
            }
            //初始化之前监听
            if (!ObjectUtils.isEmpty(server.lifecycleListenerList())) {
                for (LifecycleListener listener : server.lifecycleListenerList()) {
                    listener.beforeInit(server);
                }
            }
            //初始化代码
            server.init();

            //初始化之后监听
            if (!ObjectUtils.isEmpty(server.lifecycleListenerList())) {
                for (LifecycleListener listener : server.lifecycleListenerList()) {
                    listener.afterInit(server);
                }
            }
        }
    }

    /**
     * 启动代码
     *
     * @throws Exception e
     */
    private void startLifecycle() throws Exception {

        for (IServer server : serverList) {
            if (ObjectUtils.isEmpty(server)) {
                continue;
            }
            //初始化之前监听
            if (!ObjectUtils.isEmpty(server.lifecycleListenerList())) {
                for (LifecycleListener listener : server.lifecycleListenerList()) {
                    listener.beforeStart(server);
                }
            }
            //初始化代码
            server.start();

            //初始化之后监听
            if (!ObjectUtils.isEmpty(server.lifecycleListenerList())) {
                for (LifecycleListener listener : server.lifecycleListenerList()) {
                    listener.afterStart(server);
                }
            }
        }
    }


    /**
     * 添加拦截器
     *
     * @param interceptor interceptor
     */
    public void addPostOfficeNotifyInterceptor(PostOfficeNotifyInterceptor interceptor) {
        if (!ObjectUtils.isEmpty(interceptor)) {
            postOfficeNotifyInterceptors.add(interceptor);
        }
    }

    /**
     * 根据配置文件创建配置信息
     *
     * @return config
     */
    private IConfig createDefaultConfig() {
        IResourceLoader reader = new FileResourceLoader(defaultConfigFile());
        return new ResourceLoaderConfig(reader);
    }

    /**
     * file System ，默认配置文件位置
     *
     * @return 配置文件位置
     */
    private File defaultConfigFile() {
        String configPath = System.getProperty("octopus.path", null);
        return new File(configPath, IConfig.DEFAULT_CONFIG);
    }


    public void close() {

        try {
            stopLifecycle();

            destroyLifecycle();
        } catch (Exception e) {
            e.printStackTrace();
        }

    }


    private void stopLifecycle() throws Exception {
        for (IServer server : serverList) {
            if (ObjectUtils.isEmpty(server)) {
                continue;
            }
            //初始化之前监听
            if (!ObjectUtils.isEmpty(server.lifecycleListenerList())) {
                for (LifecycleListener listener : server.lifecycleListenerList()) {
                    listener.beforeStop(server);
                }
            }
            //初始化代码
            server.stop();

            //初始化之后监听
            if (!ObjectUtils.isEmpty(server.lifecycleListenerList())) {
                for (LifecycleListener listener : server.lifecycleListenerList()) {
                    listener.afterStop(server);
                }
            }
        }
    }


    private void destroyLifecycle() throws Exception {
        for (IServer server : serverList) {
            if (ObjectUtils.isEmpty(server)) {
                continue;
            }
            //初始化之前监听
            if (!ObjectUtils.isEmpty(server.lifecycleListenerList())) {
                for (LifecycleListener listener : server.lifecycleListenerList()) {
                    listener.beforeDestroy(server);
                }
            }
            //初始化代码
            server.destroy();

            //初始化之后监听
            if (!ObjectUtils.isEmpty(server.lifecycleListenerList())) {
                for (LifecycleListener listener : server.lifecycleListenerList()) {
                    listener.afterDestroy(server);
                }
            }
        }
    }


    public static void main(String[] args) {
        System.setProperty("io.netty.tryReflectionSetAccessible", "true");
        System.setProperty("add-opens", "java.base/jdk.internal.misc=ALL-UNNAMED");
        ServerStarter serverStarter = new ServerStarter();
//        serverStarter.addServer(new ConsoleServer());
        serverStarter.startServer();
    }

}
