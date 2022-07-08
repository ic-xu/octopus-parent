package io.octopus.scala.broker.mqtt.server

import io.netty.channel._
import io.netty.channel.epoll.{EpollEventLoopGroup, EpollServerSocketChannel}
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.ServerSocketChannel
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.util.concurrent.DefaultThreadFactory
import io.octopus.broker.metrics._
import io.octopus.broker.security.ReadWriteControl
import io.octopus.kernel.kernel.config.IConfig
import io.octopus.kernel.kernel.contants.BrokerConstants
import io.octopus.kernel.kernel.interceptor.NotifyInterceptor
import io.octopus.kernel.kernel.security.IAuthenticator
import io.octopus.kernel.kernel.session.ISessionResistor
import io.octopus.kernel.kernel.subscriptions.ISubscriptionsDirectory
import io.octopus.scala.broker.mqtt.server.transport.ITransport
import org.slf4j.{Logger, LoggerFactory}

import java.util
import java.util.ServiceLoader
import java.util.concurrent.TimeUnit

/**
 * netty acceptor
 */

class TransportBootstrap(authenticator: IAuthenticator, interceptor: NotifyInterceptor,
                         readWriteControl: ReadWriteControl) {

  private val logger: Logger = LoggerFactory.getLogger(classOf[TransportBootstrap])
  private var ports: Map[String, Int] = Map()
  private var bossGroup: EventLoopGroup = _
  private var workerGroup: EventLoopGroup = _
  private val bytesMetricsCollector = new BytesMetricsCollector
  private val metricsCollector = new MessageMetricsCollector
  private var channelClass: Class[_ <: ServerSocketChannel] = _
  private val protocolCovertHandlerList: java.util.List[ITransport] = new util.ArrayList[ITransport]()
  //流量整形
  //  private var globalTrafficShapingHandler: GlobalTrafficShapingHandler = _


  /**
   * init nettyAcceptor container
   *
   * @param config                 config
   * @param msgDispatcher          msgdispatcher
   * @param sessionRegistry        sessions
   * @param subscriptionsDirectory subs
   */
  def initialize(config: IConfig, postOffice: PostOffice,
                 sessionRegistry: ISessionResistor, subscriptionsDirectory: ISubscriptionsDirectory): Unit = {

    init(config)

    bootstrap(config, sessionRegistry, subscriptionsDirectory, postOffice)

  }

  /**
   * init config
   * @param config config
   */
  def init(config: IConfig): Unit = {

    val epoll = config.boolProp(BrokerConstants.NETTY_EPOLL_PROPERTY_NAME, false)
    if (epoll) {
      logger.info("Netty is using Epoll")
      bossGroup = new EpollEventLoopGroup(Runtime.getRuntime.availableProcessors(), new DefaultThreadFactory("boss"))
      workerGroup = new EpollEventLoopGroup(new DefaultThreadFactory("work"))
      channelClass = classOf[EpollServerSocketChannel]
    } else {
      logger.info("Netty is using NIO")
      bossGroup = new NioEventLoopGroup(Runtime.getRuntime.availableProcessors(), new DefaultThreadFactory("boss"))
      workerGroup = new NioEventLoopGroup(new DefaultThreadFactory("worker"))
      channelClass = classOf[NioServerSocketChannel]
    }

    val transports = ServiceLoader.load(classOf[ITransport])
    transports.forEach(transport => {
      logger.info("[SPI] === load ITransport.class [ {} ]", transport.getClass.getName)
      protocolCovertHandlerList.add(transport)
    })

  }

  /**
   * bootstrap config
   * @param config config
   * @param sessionFactory sessions
   * @param subscriptionsDirectory subs
   * @param postOffice postoffice
   */
  def bootstrap(config: IConfig, sessionFactory: ISessionResistor, subscriptionsDirectory: ISubscriptionsDirectory, postOffice: PostOffice): Unit = {
    protocolCovertHandlerList.forEach(protocolCovertHandler => {
      try {
        protocolCovertHandler.initProtocol(bossGroup, workerGroup, channelClass,config, sessionFactory,
          subscriptionsDirectory, postOffice, ports, authenticator, interceptor, readWriteControl)
      } catch {
        case e: Exception =>
          e.printStackTrace()
          logger.error(e.getMessage)
      }
    })
  }

  def close(): Unit = {
    logger.debug("Closing Netty acceptor...")
    if (workerGroup == null || bossGroup == null) {
      logger.error("Netty acceptor is not initialized")
      throw new IllegalStateException("Invoked close on an Acceptor that wasn't initialized")
    }
    val workerWaiter = workerGroup.shutdownGracefully
    val bossWaiter = bossGroup.shutdownGracefully
    /*
             * We shouldn't raise an IllegalStateException if we are interrupted. If we did so, the
             * broker is not shut down properly.
             */ logger.info("Waiting for worker and boss event loop groups to terminate...")
    try {
      workerWaiter.await(10, TimeUnit.SECONDS)
      bossWaiter.await(10, TimeUnit.SECONDS)
    } catch {
      case iex: InterruptedException =>
        logger.warn("An InterruptedException was caught while waiting for event loops to terminate... \n {}", iex)
    }
    if (!workerGroup.isTerminated) {
      logger.warn("Forcing shutdown of worker event loop...")
      workerGroup.shutdownGracefully(0L, 0L, TimeUnit.MILLISECONDS)
    }
    if (!bossGroup.isTerminated) {
      logger.warn("Forcing shutdown of boss event loop...")
      bossGroup.shutdownGracefully(0L, 0L, TimeUnit.MILLISECONDS)
    }
    val metrics = metricsCollector.computeMetrics
    val bytesMetrics = bytesMetricsCollector.computeMetrics
    logger.info("Metrics messages[read={}, write={}] bytes[read={}, write={}]", metrics.messagesRead, metrics.messagesWrote, bytesMetrics.readBytes, bytesMetrics.wroteBytes)
  }


}
