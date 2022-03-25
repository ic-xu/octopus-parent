package io.octopus.scala.broker

import io.handler.codec.mqtt.{IMessage, MqttPublishMessage}
import io.netty.util.concurrent.DefaultThreadFactory
import io.octopus.Version
import io.octopus.base.config._
import io.octopus.base.contants.BrokerConstants
import io.octopus.base.interfaces._
import io.octopus.base.queue.MsgQueue
import io.octopus.base.subscriptions.ISubscriptionsDirectory
import io.octopus.base.utils.{ClassLoadUtils, HostUtils, LoggingUtils, ObjectUtils}
import io.octopus.broker.security._
import io.octopus.interception.{BrokerNotifyInterceptor, InterceptHandler}
import io.octopus.scala.broker.session.SessionResistor
import io.octopus.scala.persistence.ThreadQueue
import io.store.persistence._
import io.store.persistence.disk.CheckPointServer
import io.store.persistence.maptree.TopicMapSubscriptionDirectory
import org.slf4j.{Logger, LoggerFactory}

import java.io.File
import java.net.InetSocketAddress
import java.text.ParseException
import java.util.Objects
import java.util.concurrent.{Executors, ScheduledExecutorService}
import scala.jdk.CollectionConverters._


class Server extends LifeCycle {

  private val logger: Logger = LoggerFactory.getLogger(classOf[Server])
  private var handlerList: List[InterceptHandler] = _
  private var flushDiskService: ScheduledExecutorService = _
  private var interceptor: BrokerNotifyInterceptor = _
  private var authenticator: IAuthenticator = _
  private var reController: IRWController = _
  private var store: StoreCreateFactory = _
  private var checkPointServer: CheckPointServer = _
  private var msgQueue: MsgQueue[IMessage] = _
  private var sessionFactory: SessionResistor = _
  private var postOffice: PostOffice = _
  private var acceptor: TransportBootstrap = _
  private var initialized = false
  private var routerRegister: IRouterRegister = _
  private var listenerPort = BrokerConstants.UDP_TRANSPORT_DEFAULT_PORT

  /**
   * start life or the container
   */
  override def start(): Unit = {
    val startTime = System.currentTimeMillis()
    val reader: IResourceLoader = new FileResourceLoader(defaultConfigFile())
    val config: IConfig = new ResourceLoaderConfig(reader)
    startServer(config)
    val startServerTime: Long = System.currentTimeMillis - startTime
    logger.info("{} ,version {} ,build package time {} has been started successfully in {} ms",
      Version.PROJECT_NAME,Version.VERSION, Version.TIMESTAMP,startServerTime)
  }

  /**
   * start life or the container
   */
  override def start(args: Array[String]): Unit = {
    logger.info("start {} ,version {} ,package build time {}", Version.PROJECT_NAME, Version.VERSION, Version.TIMESTAMP)
  }

  /**
   * start server with config
   *
   * @param config config
   */
  def startServer(config: IConfig): Unit = {
    listenerPort = config.getIntegerProperty("udp.port", BrokerConstants.UDP_TRANSPORT_DEFAULT_PORT)
    startServer(config, null, null)
  }


  /**
   * start server with some container
   *
   * @param config             config
   * @param handlers           interceptorList
   * @param authorizatorPolicy authenticator
   */
  def startServer(config: IConfig, handlers: List[_ <: InterceptHandler], authorizatorPolicy: IRWController): Unit = {
    if (ObjectUtils.isEmpty(handlers)) {
      this.handlerList = List.empty[InterceptHandler]
    }
    logger.trace("Starting Octopus Server. MQTT message interceptors={}", LoggingUtils.getInterceptorIds(this.handlerList.asJava))
    flushDiskService = Executors.newSingleThreadScheduledExecutor(new DefaultThreadFactory("flush-disk-server"))
    val handlerProp = System.getProperty(BrokerConstants.INTERCEPT_HANDLER_PROPERTY_NAME)
    if (!ObjectUtils.isEmpty(handlers)) {
      config.setProperty(BrokerConstants.INTERCEPT_HANDLER_PROPERTY_NAME, handlerProp)
    }
    initInterceptors(config, this.handlerList)
    logger.debug("Initialized MQTT protocol processor")

    initializeAuthenticator(authenticator, config)
    initializePermissionControl(authorizatorPolicy, config)

    store = new StoreCreateFactory(config)
    val subscriptionsRepository: ISubscriptionsRepository = store.createISubscriptionsRepository()
    val queueRepository: IQueueRepository = store.createIQueueRepository()
    val retainedRepository: IRetainedRepository = store.createIRetainedRepository()

    val subscriptions: ISubscriptionsDirectory = new TopicMapSubscriptionDirectory
    subscriptions.init(subscriptionsRepository)
    val readWriteControl: ReadWriteControl = new ReadWriteControl(this.reController)
    this.checkPointServer = new CheckPointServer
    //    this.msgQueue = new ConcurrentFileQueue(null, checkPointServer)
    this.msgQueue = new ThreadQueue(config, null, checkPointServer, flushDiskService)
    sessionFactory = new SessionResistor(subscriptions, queueRepository, readWriteControl, msgQueue)
    postOffice = new PostOffice(subscriptions, retainedRepository, sessionFactory, interceptor, readWriteControl)
    //init queue
    initMsgQueue()

    //    this.sessionFactory = new SessionRegistry(subscriptions, queueRepository, authorizator, msgQueue)
    //    this.postOffice = new MsgDispatcher(subscriptions, retainedRepository, sessionFactory, interceptor, authorizator)
    val registerUser: String = config.getProperty(BrokerConstants.REGISTER_CENTER_USER, null)
    if (null != registerUser) {
      this.postOffice.addRegisterUserName(registerUser.split(","))
    }

    acceptor = new TransportBootstrap(authenticator, interceptor, readWriteControl, msgQueue)
    acceptor.initialize(config, postOffice, sessionFactory, subscriptions)
    //    initDispatchMsgServiceService()
    //    initFlushDiskService(config)

  }


  def initPersistencePath(persistencePath: String): Unit = {
    logger.info("Initializing H2 store")
    if (ObjectUtils.isEmpty(persistencePath)) throw new IllegalArgumentException("H2 store path can't be null or empty")
    val directory = new File(persistencePath)
    if (!directory.getParentFile.exists) directory.getParentFile.mkdirs
  }

  /**
   * init initMsgQueue
   */
  private def initMsgQueue(): Unit = {
    msgQueue.initialize(postOffice)
  }

  private def initializeAuthenticator(authenticator: IAuthenticator, config: IConfig): Unit = {

    logger.debug("Configuring MQTT authenticator")
    val authenticatorClassName: String = config.getProperty(BrokerConstants.AUTHENTICATOR_CLASS_NAME, "")
    if (ObjectUtils.isEmpty(authenticator) && authenticatorClassName.nonEmpty) {
      this.authenticator = ClassLoadUtils.loadClass(this.getClass.getClassLoader, authenticatorClassName, classOf[IAuthenticator], classOf[IConfig], config)
    }

    val resourceLoader: IResourceLoader = config.getResourceLoader
    if (authenticator == null) {
      val passwdPath: String = config.getProperty(BrokerConstants.PASSWORD_FILE_PROPERTY_NAME, "")
      if (passwdPath.isEmpty) {
        this.authenticator = new AcceptAllAuthenticator
      }
      else {
        this.authenticator = new ResourceAuthenticator(resourceLoader, passwdPath)
      }
      logger.info("An {} authenticator instance will be used", this.authenticator.getClass.getName)
    }
  }

  def initializePermissionControl(authorityController: IRWController, config: IConfig): Unit = {
    val authorizatorClassName = config.getProperty(BrokerConstants.AUTHORIZATOR_CLASS_NAME, "")
    if (authorityController == null && authorizatorClassName.nonEmpty) {
      this.reController = ClassLoadUtils.loadClass(this.getClass.getClassLoader, authorizatorClassName, classOf[IRWController], classOf[IConfig], config)
    }

    if (authorityController == null) {
      val aclFilePath = config.getProperty(BrokerConstants.ACL_FILE_PROPERTY_NAME, "")
      if (aclFilePath != null && aclFilePath.nonEmpty) {
        this.reController = new DenyAllAuthorityController
        try {
          logger.info("Parsing ACL file. Path = {}", aclFilePath)
          val resourceLoader = config.getResourceLoader
          this.reController = ACLFileParser.parse(resourceLoader.loadResource(aclFilePath))
        } catch {
          case pex: ParseException =>
            logger.error("Unable to parse ACL file. path = {}", aclFilePath, pex)
        }
      } else {
        this.reController = new PermitAllAuthorityController
      }
      logger.info("Authorizator policy {} instance will be used", this.reController.getClass.getName)
    }

  }

  /**
   * init InterceptHandlerListener
   *
   * @param props             config
   * @param embeddedObservers listener
   */
  private def initInterceptors(props: IConfig, embeddedObservers: List[InterceptHandler]): Unit = {
    logger.info("Configuring message interceptors...")
    val interceptorClassName = props.getProperty(BrokerConstants.INTERCEPT_HANDLER_PROPERTY_NAME)
    var observers = embeddedObservers
    if (interceptorClassName != null && interceptorClassName.nonEmpty) {
      val handler = ClassLoadUtils.loadClass(this.getClass.getClassLoader, interceptorClassName, classOf[InterceptHandler], classOf[Server], this)
      if (handler != null) {
        observers = embeddedObservers :+ handler
      }
    }
    this.interceptor = new BrokerNotifyInterceptor(props, observers.asJava)
  }

  //file System
  private def defaultConfigFile() = {
    val configPath = System.getProperty("octopus.path", null)
    new File(configPath, IConfig.DEFAULT_CONFIG)
  }


  /**
   * Use the broker to publish a message. It's intended for embedding applications. It can be used
   * only after the integration is correctly started with startServer.
   *
   * @param msg      the message to forward.
   * @param clientId the id of the sending integration.
   * @throws IllegalStateException if the integration is not yet started
   */
  def internalPublish(msg: MqttPublishMessage, clientId: String): Unit = {
    val messageID = msg.variableHeader.packetId
    if (!initialized) {
      logger.error("Octopus is not started, internal message cannot be published. CId: {}, messageId: {}", clientId, messageID)
      throw new IllegalStateException("Can't publish on a integration is not yet started")
    }
    logger.trace("Internal publishing message CId: {}, messageId: {}", clientId, messageID)
    postOffice.internalPublish(msg)
    msg.payload.release
  }

  /**
   * stop life or container
   */
  override def stop(): Unit = {
    logger.info("begin stop server ...")
    if (!ObjectUtils.isEmpty(msgQueue)) msgQueue.stop()

    logger.info("Unbinding integration from the configured ports")
    acceptor.close()
    logger.trace("Stopping MQTT protocol processor")
    initialized = false
    // calling shutdown() does not actually stop tasks that are not cancelled,
    // and SessionsRepository does not stop its tasks. Thus shutdownNow().
    store.stop()
    flushDiskService.shutdownNow
    val routerRegister = getClusterTopicRouter
    if (null != routerRegister) routerRegister.remove(new InetSocketAddress(Objects.requireNonNull(HostUtils.getPath), listenerPort))
    interceptor.stop()
    logger.info("Octopus integration has been stopped.")

  }

  def getClusterTopicRouter: IRouterRegister = routerRegister

}


object Server {

  /**
   * main
   *
   * @param args args
   */
  def main(args: Array[String]): Unit = {
    System.setProperty("io.netty.tryReflectionSetAccessible", "true")
    System.setProperty("add-opens", "java.base/jdk.internal.misc=ALL-UNNAMED")
    val server = new Server()
    server.start()

    //Bind a shutdown hook
    Runtime.getRuntime.addShutdownHook(new Thread(() => server.stop()))
  }

}