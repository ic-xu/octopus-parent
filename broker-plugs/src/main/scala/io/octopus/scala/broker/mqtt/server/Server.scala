package io.octopus.scala.broker.mqtt.server

import io.netty.util.ReferenceCountUtil
import io.netty.util.concurrent.DefaultThreadFactory
import io.octopus.Version
import io.octopus.config.{FileResourceLoader, IConfig, IResourceLoader, ResourceLoaderConfig}
import io.octopus.contants.BrokerConstants
import io.octopus.kernel.kernel._
import io.octopus.kernel.kernel.interceptor.{ConnectionNotifyInterceptor, PostOfficeNotifyInterceptor}
import io.octopus.kernel.kernel.listener.LifecycleListener
import io.octopus.kernel.kernel.message.KernelPayloadMessage
import io.octopus.kernel.kernel.repository.{IQueueRepository, IRetainedRepository, IStoreCreateFactory, ISubscriptionsRepository}
import io.octopus.kernel.kernel.router.IRouterRegister
import io.octopus.kernel.kernel.security._
import io.octopus.kernel.kernel.subscriptions.ISubscriptionsDirectory
import io.octopus.kernel.utils.{ClassLoadUtils, HostUtils, ObjectUtils}
import io.octopus.scala.broker.mqtt.persistence.MemoryQueue
import io.octopus.utils.LoggingUtils
import io.store.persistence._
import io.store.persistence.disk.CheckPointServer
import io.store.persistence.maptree.TopicMapSubscriptionDirectory
import org.slf4j.{Logger, LoggerFactory}

import java.io.File
import java.net.InetSocketAddress
import java.text.ParseException
import java.util
import java.util.Objects
import java.util.concurrent.{Executors, ScheduledExecutorService}
import scala.jdk.CollectionConverters._


class Server extends IServer {

  private val logger: Logger = LoggerFactory.getLogger(classOf[Server])
  private var kernelInterceptor: List[PostOfficeNotifyInterceptor] = _
  private var flushDiskService: ScheduledExecutorService = _
  private var interceptors: List[ConnectionNotifyInterceptor] = _
  private var authenticator: IAuthenticator = _
  private var reController: IRWController = _
  private var store: IStoreCreateFactory = _
  private var checkPointServer: CheckPointServer = _
  private var sessionResistor: DefaultSessionResistor = _
  private var postOffice: IPostOffice = _
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
      Version.PROJECT_NAME, Version.VERSION, Version.TIMESTAMP, startServerTime)
  }

  /**
   * start life or the container
   */
  override def start(args: Array[String]): Unit = {
    logger.info("start {} ,version {} ,package build time {}", Version.PROJECT_NAME, Version.VERSION, Version.TIMESTAMP)
    start()
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
  def startServer(config: IConfig, handlers: List[_ <: PostOfficeNotifyInterceptor], authorizatorPolicy: IRWController): Unit = {
    if (ObjectUtils.isEmpty(handlers)) {
      this.kernelInterceptor = List.empty[PostOfficeNotifyInterceptor]
    }
    logger.trace("Starting Octopus Server. MQTT message interceptors={}", LoggingUtils.getInterceptorIds(this.kernelInterceptor.asJava))


    flushDiskService = Executors.newSingleThreadScheduledExecutor(new DefaultThreadFactory("flush-disk-server"))
    val handlerProp = System.getProperty(BrokerConstants.INTERCEPT_HANDLER_PROPERTY_NAME)
    if (!ObjectUtils.isEmpty(handlers)) {
      config.setProperty(BrokerConstants.INTERCEPT_HANDLER_PROPERTY_NAME, handlerProp)
    }
    initInterceptors(config, this.kernelInterceptor)
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

    sessionResistor = new DefaultSessionResistor(queueRepository, readWriteControl, config, new MemoryQueue(config, checkPointServer))
    postOffice = new DefaultPostOffice(subscriptions, retainedRepository, sessionResistor, this.kernelInterceptor.asJava, readWriteControl)
    sessionResistor.setPostOffice(postOffice)

    //    this.sessionResistor = new SessionRegistry(subscriptions, queueRepository, authorizator, msgQueue)
    //    this.postOffice = new MsgDispatcher(subscriptions, retainedRepository, sessionResistor, interceptor, authorizator)
    val registerUser: String = config.getProperty(BrokerConstants.REGISTER_CENTER_USER, null)
    if (null != registerUser) {
      this.postOffice.addAdminUser(registerUser.split(","))
    }

    acceptor = new TransportBootstrap(authenticator, interceptors.asJava, readWriteControl)
    acceptor.initialize(config, postOffice, sessionResistor, subscriptions)
    //        initDispatchMsgServiceService()
    //        initFlushDiskService(config)

  }


  def initPersistencePath(persistencePath: String): Unit = {
    logger.info("Initializing H2 store")
    if (ObjectUtils.isEmpty(persistencePath)) throw new IllegalArgumentException("H2 store path can't be null or empty")
    val directory = new File(persistencePath)
    if (!directory.getParentFile.exists) directory.getParentFile.mkdirs
  }

  //  /**
  //   * init initMsgQueue
  //   */
  //  private def initMsgQueue(): Unit = {
  //    msgQueue.init(postOffice)
  //  }

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
  private def initInterceptors(props: IConfig, embeddedObservers: List[PostOfficeNotifyInterceptor]): Unit = {
    logger.info("Configuring message interceptors...")
    val interceptorClassName = props.getProperty(BrokerConstants.INTERCEPT_HANDLER_PROPERTY_NAME)
    var observers = embeddedObservers
    if (interceptorClassName != null && interceptorClassName.nonEmpty) {
      val handler = ClassLoadUtils.loadClass(this.getClass.getClassLoader, interceptorClassName, classOf[PostOfficeNotifyInterceptor], classOf[Server], this)
      if (handler != null) {
        observers = embeddedObservers :+ handler
      }
    }
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
  def internalPublish(msg: KernelPayloadMessage, clientId: String): Unit = {
    if (!initialized) {
      logger.error("Octopus is not started, internal message cannot be published. CId: {}, messageId: {}", clientId, msg.packageId())
      throw new IllegalStateException("Can't publish on a integration is not yet started")
    }
    logger.trace("Internal publishing message CId: {}, messageId: {}", clientId, msg.packageId())

    postOffice.internalPublish(msg)
    ReferenceCountUtil.safeRelease(msg.getPayload)
  }

  /**
   * stop life or container
   */
  override def stop(): Unit = {
    logger.info("begin stop server ...")

    logger.info("Unbinding integration from the configured ports")
    acceptor.close()
    logger.trace("Stopping MQTT protocol processor")
    initialized = false
    // calling shutdown() does not actually stop tasks that are not cancelled,
    // and SessionsRepository does not stop its tasks. Thus shutdownNow().
    store.stop()
    flushDiskService.shutdownNow
    val routerRegister = getClusterTopicRouter
    var address: String = Objects.requireNonNull(HostUtils.getAnyIpv4Address)
    if (null != routerRegister) routerRegister.remove(new InetSocketAddress(address, listenerPort))
    logger.info("Octopus integration has been stopped.")

  }

  def getClusterTopicRouter: IRouterRegister = routerRegister

  /**
   * 初始化方法
   *
   * @throws Exception e
   */
  override def init(): Unit = {}

  /**
   * 方法销毁之后调用
   */
  override def destroy(): Unit = {
  }

  /**
   * 服务详细信息
   *
   * @return
   */
  override def serviceDetails(): ServiceDetails = {
    new ServiceDetails()
  }

  /**
   * 获取生命周期监听器
   *
   * @return 返回一个list数组
   */
  override def lifecycleListenerList(): util.List[LifecycleListener] = {
    new util.ArrayList[LifecycleListener]()
  }
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


  }

}