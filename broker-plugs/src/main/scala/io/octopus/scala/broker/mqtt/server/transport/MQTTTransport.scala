package io.octopus.scala.broker.mqtt.server.transport

import io.handler.codec.mqtt.{MqttDecoder, MqttEncoder}
import io.netty.channel._
import io.netty.channel.socket.{ServerSocketChannel, SocketChannel}
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler
import io.netty.handler.codec.http.{HttpObjectAggregator, HttpRequestDecoder, HttpResponseEncoder, HttpServerCodec}
import io.netty.handler.logging.{LogLevel, LoggingHandler}
import io.netty.handler.ssl.{SslContext, SslHandler}
import io.netty.handler.timeout.IdleStateHandler
import io.octopus.broker.handler._
import io.octopus.broker.security.DefaultOctopusSslContextCreator
import io.octopus.config.IConfig
import io.octopus.contants.BrokerConstants
import io.octopus.kernel.kernel.{BaseTransport, IPostOffice, ISessionResistor}
import io.octopus.kernel.kernel.interceptor.ConnectionNotifyInterceptor
import io.octopus.kernel.kernel.metrics.{BytesMetricsCollector, MessageMetricsCollector}
import io.octopus.kernel.kernel.security.{IAuthenticator, ReadWriteControl}
import io.octopus.kernel.kernel.security.ssl.ISslContextCreator
import io.octopus.kernel.kernel.subscriptions.ISubscriptionsDirectory
import io.octopus.scala.broker.mqtt.factory.MQTTConnectionFactory
import io.octopus.scala.broker.mqtt.server.handler.{BeforeInterceptorHandler, NettyMQTTHandler}
import org.slf4j.{Logger, LoggerFactory}

import java.util.concurrent.TimeUnit

/**
 * @author chenxu
 * @version 1
 */

class MQTTTransport extends BaseTransport {

  private val logger: Logger = LoggerFactory.getLogger(classOf[MQTTTransport])

  private val beforeInterceptorHandler: BeforeInterceptorHandler = new BeforeInterceptorHandler()
  private val bytesMetricsCollector = new BytesMetricsCollector
  private val metricsCollector = new MessageMetricsCollector
  protected var mqttHandler: NettyMQTTHandler = _


  override def initProtocol(bossGroup: EventLoopGroup, workerGroup: EventLoopGroup, channelClass: Class[_ <: ServerSocketChannel],
                            config: IConfig, sessionRegistry: ISessionResistor,
                            subscriptionsDirectory: ISubscriptionsDirectory,
                            msgDispatcher: IPostOffice,
                            ports: java.util.Map[String, Integer], authenticator: IAuthenticator,
                            interceptors: java.util.List[ConnectionNotifyInterceptor], readWriteControl: ReadWriteControl): Unit = {

    /**
     * 初始化参数
     */
    initialize(bossGroup, workerGroup, channelClass, config, msgDispatcher, sessionRegistry, ports, authenticator, interceptors, readWriteControl)

    val sslCtxCreator: ISslContextCreator = new DefaultOctopusSslContextCreator(config)


    val connectionFactory = new MQTTConnectionFactory(brokerConfig, authenticator, sessionRegistry, msgDispatcher, interceptors, readWriteControl)

    mqttHandler = new NettyMQTTHandler(connectionFactory)


    /*
    * init SSL netty server
    */
    if (securityPortsConfigured(config)) {
      val sslContext = sslCtxCreator.initSSLContext
      if (sslContext == null) {
        logger.error("Can't initialize SSLHandler layer! Exiting, check your configuration of jks")
        return
      }
      initializeSSLTCPTransport(mqttHandler, config, sslContext)
      initializeWSSTransport(mqttHandler, config, sslContext)
      /* 证书没有经过签证的证书，所以https 不能使用 */
      //            initializeHttpsTransport(mqttHandler, config,sessions, sslContext);
    }


    /*
     * init netty server
     */
    initializePlainTCPTransport(mqttHandler, config)


    initializeWebSocketTransport(mqttHandler, config)
  }


  /**
   * if config ssl
   *
   * @param props config
   * @return boolean
   */
  private def securityPortsConfigured(props: IConfig): Boolean = {
    val sslTcpPortProp = props.getProperty(BrokerConstants.SSL_PORT_PROPERTY_NAME)
    val wssPortProp = props.getProperty(BrokerConstants.WSS_PORT_PROPERTY_NAME)
    sslTcpPortProp != null || wssPortProp != null
  }


  def initializeSSLTCPTransport(mqttHandler: NettyMQTTHandler, config: IConfig, sslContext: SslContext): Unit = {
    logger.debug("Configuring SSL MQTT transport")
    val sslPortProp = config.getProperty(BrokerConstants.SSL_PORT_PROPERTY_NAME, BrokerConstants.DISABLED_PORT_BIND)
    if (BrokerConstants.DISABLED_PORT_BIND.equals(sslPortProp)) { // Do nothing no SSL configured
      logger.info("Property {} has been set to {}. SSL MQTT will be disabled", BrokerConstants.SSL_PORT_PROPERTY_NAME, BrokerConstants.DISABLED_PORT_BIND)
      return
    }

    val sslPort = sslPortProp.toInt
    logger.debug("Starting SSL on port {}", sslPort)

    val timeoutHandler = new OctopusIdleTimeoutHandler
    val host = config.getProperty(BrokerConstants.HOST_PROPERTY_NAME)
    val sNeedsClientAuth = config.getProperty(BrokerConstants.NEED_CLIENT_AUTH, "false")
    initTcpTransportFactory(host, sslPort, BrokerConstants.SSL_MQTT_PROTO, channel => {
      val pipeline: ChannelPipeline = channel.pipeline()
      pipeline.addLast("ssl", createSslHandler(channel, sslContext, sNeedsClientAuth.toBoolean))
      configureMQTTPipeline(pipeline, timeoutHandler, mqttHandler)
    })
  }


  def initializeWSSTransport(mqttHandler: NettyMQTTHandler, config: IConfig, sslContext: SslContext): Unit = {
    logger.debug("Configuring secure websocket MQTT transport")
    val sslPortProp = config.getProperty(BrokerConstants.WSS_PORT_PROPERTY_NAME, BrokerConstants.DISABLED_PORT_BIND)
    if (BrokerConstants.DISABLED_PORT_BIND.equals(sslPortProp)) { // Do nothing no SSL configured
      logger.info("Property {} has been set to {}. Secure websocket MQTT will be disabled", BrokerConstants.WSS_PORT_PROPERTY_NAME, BrokerConstants.DISABLED_PORT_BIND)
      return
    }
    val sslPort = sslPortProp.toInt
    val timeoutHandler = new OctopusIdleTimeoutHandler
    val host = config.getProperty(BrokerConstants.HOST_PROPERTY_NAME)
    val path = config.getProperty(BrokerConstants.WEB_SOCKET_PATH_PROPERTY_NAME, BrokerConstants.WEBSOCKET_PATH)
    val maxFrameSize = config.intProp(BrokerConstants.WEB_SOCKET_MAX_FRAME_SIZE_PROPERTY_NAME, 65536)
    val sNeedsClientAuth = config.getProperty(BrokerConstants.NEED_CLIENT_AUTH, "false")
    initTcpTransportFactory(host, sslPort, "Secure websocket", channel => {
      val pipeline = channel.pipeline
      pipeline.addLast("ssl", createSslHandler(channel, sslContext, sNeedsClientAuth.toBoolean))
      pipeline.addLast("httpEncoder", new HttpResponseEncoder)
      pipeline.addLast("httpDecoder", new HttpRequestDecoder)
      pipeline.addLast("aggregator", new HttpObjectAggregator(65536))
      pipeline.addLast("webSocketHandler", new WebSocketServerProtocolHandler(path, BrokerConstants.MQTT_SUBPROTOCOL_CSV_LIST, false, maxFrameSize))
      pipeline.addLast("ws2bytebufDecoder", new WebSocketDecoder)
      pipeline.addLast("bytebuf2wsEncoder", new WebSocketEncoder)
      configureMQTTPipeline(pipeline, timeoutHandler, mqttHandler)
    })
  }


  def initializePlainTCPTransport(mqttHandler: NettyMQTTHandler, config: IConfig): Unit = {

    logger.debug("Configuring TCP MQTT transport")
    val timeoutHandler = new OctopusIdleTimeoutHandler
    val host = config.getProperty(BrokerConstants.HOST_PROPERTY_NAME)
    val tcpPortProp = config.getProperty(BrokerConstants.PORT_PROPERTY_NAME, BrokerConstants.DISABLED_PORT_BIND)
    if (BrokerConstants.DISABLED_PORT_BIND.equals(tcpPortProp)) {
      logger.info("Property {} has been set to {}. TCP MQTT will be disabled", BrokerConstants.PORT_PROPERTY_NAME, BrokerConstants.DISABLED_PORT_BIND)
      return
    }
    val port = tcpPortProp.toInt
    initTcpTransportFactory(host, port, BrokerConstants.PLAIN_MQTT_PROTO, channel => {
      val pipeline = channel.pipeline
      configureMQTTPipeline(pipeline, timeoutHandler, mqttHandler)
    })
  }




  def initializeWebSocketTransport(mqttHandler: NettyMQTTHandler, config: IConfig): Unit = {
    logger.debug("Configuring Websocket MQTT transport")
    val webSocketPortProp = config.getProperty(BrokerConstants.WEB_SOCKET_PORT_PROPERTY_NAME, BrokerConstants.DISABLED_PORT_BIND)
    if (BrokerConstants.DISABLED_PORT_BIND.equals(webSocketPortProp)) { // Do nothing no WebSocket configured
      logger.info("Property {} has been setted to {}. Websocket MQTT will be disabled", BrokerConstants.WEB_SOCKET_PORT_PROPERTY_NAME, BrokerConstants.DISABLED_PORT_BIND)
      return
    }
    val port = webSocketPortProp.toInt

    val timeoutHandler = new OctopusIdleTimeoutHandler

    val host = config.getProperty(BrokerConstants.HOST_PROPERTY_NAME)
    val path = config.getProperty(BrokerConstants.WEB_SOCKET_PATH_PROPERTY_NAME, BrokerConstants.WEBSOCKET_PATH)
    val maxFrameSize = config.intProp(BrokerConstants.WEB_SOCKET_MAX_FRAME_SIZE_PROPERTY_NAME, 65536)
    initTcpTransportFactory(host, port, "Websocket MQTT", channel => {
      val pipeline = channel.pipeline
      pipeline.addLast(new HttpServerCodec)
      pipeline.addLast("aggregator", new HttpObjectAggregator(maxFrameSize))
      pipeline.addLast("webSocketHandler", new WebSocketServerProtocolHandler(path, BrokerConstants.MQTT_SUBPROTOCOL_CSV_LIST, false, maxFrameSize))
      pipeline.addLast("ContinuationWebSocketFrameHandler", new ContinuationWebSocketFrameHandler)
      pipeline.addLast("ws2bytebufDecoder", new WebSocketDecoder)
      pipeline.addLast("bytebuf2wsEncoder", new WebSocketEncoder)
      configureMQTTPipeline(pipeline, timeoutHandler, mqttHandler)

    })
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

  private def createSslHandler(channel: SocketChannel, sslContext: SslContext, needsClientAuth: Boolean): ChannelHandler = {
    val sslEngine = sslContext.newEngine(channel.alloc, channel.remoteAddress.getHostString, channel.remoteAddress.getPort)
    sslEngine.setUseClientMode(false)
    if (needsClientAuth) sslEngine.setNeedClientAuth(true)
    new SslHandler(sslEngine)
  }

}
