package io.octopus.scala.broker.mqtt.server.transport

import io.handler.codec.mqtt.{MqttDecoder, MqttEncoder}
import io.netty.bootstrap.ServerBootstrap
import io.netty.channel._
import io.netty.channel.socket.{ServerSocketChannel, SocketChannel}
import io.netty.handler.logging.{LogLevel, LoggingHandler}
import io.netty.handler.timeout.IdleStateHandler
import io.octopus.broker.handler._
import io.octopus.broker.metrics.{BytesMetricsCollector, MessageMetricsCollector}
import io.octopus.broker.security.ReadWriteControl
import io.octopus.kernel.kernel.config.{BrokerConfiguration, IConfig}
import io.octopus.kernel.kernel.contants.BrokerConstants
import io.octopus.kernel.kernel.interceptor.NotifyInterceptor
import io.octopus.kernel.kernel.security.IAuthenticator
import io.octopus.kernel.kernel.session.ISessionResistor
import io.octopus.scala.broker.mqtt.factory.MQTTConnectionFactory
import io.octopus.scala.broker.mqtt.server.handler.{BeforeInterceptorHandler, NettyMQTTHandler}
import io.octopus.scala.broker.mqtt.server.listener.BindLocalPortListener
import io.octopus.scala.broker.mqtt.server.{PipelineInitializer, PostOffice, handler}
import org.slf4j.{Logger, LoggerFactory}

import java.util.Optional
import java.util.concurrent.TimeUnit

/**
 * @author chenxu
 * @version 1
 */

abstract class BaseTransport extends ITransport {

  private val logger: Logger = LoggerFactory.getLogger(classOf[BaseTransport])

  private var nettySoBacklog: Integer = 0
  private var nettySoReuseaddr: Boolean = false
  private var nettyTcpNodelay: Boolean = false
  private var nettySoKeepalive: Boolean = false
  private var nettyChannelTimeoutSeconds = 0
  private var maxBytesInMessage = 0
  private var bossGroup: EventLoopGroup = _
  private var workerGroup: EventLoopGroup = _


  private val bytesMetricsCollector = new BytesMetricsCollector
  private val metricsCollector = new MessageMetricsCollector
  private var metrics: Optional[handler.DropWizardMetricsHandler] = _
  private var channelClass: Class[_ <: ServerSocketChannel] = _
  private val beforeInterceptorHandler: BeforeInterceptorHandler = new BeforeInterceptorHandler()
  private var brokerConfig: BrokerConfiguration = _
  protected var mqttHandler: NettyMQTTHandler = _
  private var ports: Map[String, Int] = _




  def initialize(bossGroup: EventLoopGroup, workerGroup: EventLoopGroup, channelClass: Class[_ <: ServerSocketChannel],
                 config: IConfig, msgDispatcher: PostOffice,
                 sessionRegistry: ISessionResistor,
                 ports: Map[String, Int], authenticator: IAuthenticator,
                 interceptor: NotifyInterceptor,
                 readWriteControl: ReadWriteControl): Unit = {


    brokerConfig = new BrokerConfiguration(config)
    val connectionFactory = new MQTTConnectionFactory(brokerConfig, authenticator, sessionRegistry, msgDispatcher, interceptor, readWriteControl)

    mqttHandler = new NettyMQTTHandler(connectionFactory)
    this.ports = ports
    nettySoBacklog = config.intProp(BrokerConstants.NETTY_SO_BACKLOG_PROPERTY_NAME, 1024)
    nettySoReuseaddr = config.boolProp(BrokerConstants.NETTY_SO_REUSEADDR_PROPERTY_NAME, true)
    nettyTcpNodelay = config.boolProp(BrokerConstants.NETTY_TCP_NODELAY_PROPERTY_NAME, true)
    nettySoKeepalive = config.boolProp(BrokerConstants.NETTY_SO_KEEPALIVE_PROPERTY_NAME, true)
    nettyChannelTimeoutSeconds = config.intProp(BrokerConstants.NETTY_CHANNEL_TIMEOUT_SECONDS_PROPERTY_NAME, 10)
    maxBytesInMessage = config.intProp(BrokerConstants.NETTY_MAX_BYTES_PROPERTY_NAME, BrokerConstants.DEFAULT_NETTY_MAX_BYTES_IN_MESSAGE)

    this.bossGroup = bossGroup
    this.workerGroup = workerGroup
    this.channelClass =channelClass

    //    globalTrafficShapingHandler = new GlobalTrafficShapingHandler(workerGroup, 1024 * 1024, 5, 10, 5);

    val useFineMetrics = config.boolProp(BrokerConstants.METRICS_ENABLE_PROPERTY_NAME, false)
    if (useFineMetrics) {
      val metricsHandler = new handler.DropWizardMetricsHandler(msgDispatcher)
      metricsHandler.init(config)
      this.metrics = Optional.of(metricsHandler)
    } else {
      this.metrics = Optional.empty
    }
  }



  protected def initTcpTransportFactory(host: String, port: Int, protocol: String, pipelineInitializer: PipelineInitializer): Unit = {
    logger.debug("Initializing integration. Protocol={}", protocol)
    val b: ServerBootstrap = new ServerBootstrap()
    b.group(bossGroup, workerGroup)
      .channel(channelClass)
      .childHandler(new ChannelInitializer[SocketChannel] {
        override def initChannel(c: SocketChannel): Unit = pipelineInitializer.init(c)
      })
      //设置控制tcp 三次握手过程中全链接队列大小。
      .option(ChannelOption.SO_BACKLOG, nettySoBacklog)

      //设置地址可重用（作用是尽早的让地址可用）
      .option(ChannelOption.SO_REUSEADDR, boolean2Boolean(nettySoReuseaddr))

      //TCP 的Nagle 算法。
      .childOption(ChannelOption.TCP_NODELAY, boolean2Boolean(nettyTcpNodelay))

      .childOption(ChannelOption.SO_SNDBUF, int2Integer(maxBytesInMessage * 3))

      .childOption(ChannelOption.SO_KEEPALIVE, boolean2Boolean(nettySoKeepalive))
    try {
      logger.debug("Binding integration. host={}, port={}", host, port)
      // Bind and start to accept incoming connections.
      val f = b.bind(host, port)
      logger.info("Server bound to host={}, port={}, protocol={}", host, port, protocol)
      f.sync.addListener(new BindLocalPortListener(protocol, ports)).addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE)
    } catch {
      case ex: Exception =>
        logger.error("An interruptedException was caught while initializing integration. Protocol={}", protocol, ex)
        throw new RuntimeException(ex)
    }
  }


  def configureMQTTPipeline(pipeline: ChannelPipeline, timeoutHandler: OctopusIdleTimeoutHandler, mqttHandler: NettyMQTTHandler): Unit = {

    pipeline.addFirst("idleStateHandler", new IdleStateHandler(nettyChannelTimeoutSeconds, 0, 0))
    pipeline.addAfter("idleStateHandler", "timeoutHandler", timeoutHandler)
    if (brokerConfig.isOpenNettyLogger) {
      pipeline.addLast("logger", new LoggingHandler("Netty", LogLevel.INFO))
      logger.info("pipeline add NettyLogger Handler")
    }
    pipeline.addFirst("byteMetrics", new BytesMetricsHandler(bytesMetricsCollector))
    if (!brokerConfig.isImmediateBufferFlush) {
      pipeline.addLast("autoFlush", new AutoFlushHandler(10, TimeUnit.MILLISECONDS))
      logger.info("pipeline add autoFlush Handler")
    }
    pipeline.addLast("decoder", new MqttDecoder(maxBytesInMessage))
    pipeline.addLast("encoder", MqttEncoder.INSTANCE)
    pipeline.addLast("metrics", new MessageMetricsHandler(metricsCollector))
    pipeline.addLast("messageLogger", new MQTTMessageLoggerHandler)

    metrics.ifPresent(channelInboundHandler => pipeline.addLast("wizardMetrics", channelInboundHandler))
    pipeline.addLast("beforeInterceptor", beforeInterceptorHandler)

    pipeline.addLast("handler", mqttHandler)
  }


}
