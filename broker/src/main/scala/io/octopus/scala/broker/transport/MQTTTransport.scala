package io.octopus.scala.broker.transport

import io.handler.codec.mqtt.IMessage
import io.netty.channel._
import io.netty.channel.socket.{ServerSocketChannel, SocketChannel}
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler
import io.netty.handler.codec.http.{HttpObjectAggregator, HttpRequestDecoder, HttpResponseEncoder, HttpServerCodec}
import io.netty.handler.ssl.{SslContext, SslHandler}
import io.octopus.base.config.IConfig
import io.octopus.base.contants.BrokerConstants
import io.octopus.base.interfaces.{IAuthenticator, ISslContextCreator}
import io.octopus.base.queue.MsgQueue
import io.octopus.base.subscriptions.ISubscriptionsDirectory
import io.octopus.broker.handler._
import io.octopus.broker.security.{DefaultOctopusSslContextCreator, ReadWriteControl}
import io.octopus.interception.BrokerNotifyInterceptor
import io.octopus.scala.broker.PostOffice
import io.octopus.scala.broker.handler.NettyMQTTHandler
import io.octopus.scala.broker.session.SessionResistor
import org.slf4j.{Logger, LoggerFactory}

/**
 * @author chenxu
 * @version 1
 */

class MQTTTransport extends BaseTransport {

  private val logger: Logger = LoggerFactory.getLogger(classOf[MQTTTransport])




  override def initProtocol(bossGroup: EventLoopGroup, workerGroup: EventLoopGroup, channelClass: Class[_ <: ServerSocketChannel],
                            config: IConfig, sessionRegistry: SessionResistor,
                            subscriptionsDirectory: ISubscriptionsDirectory,
                            msgDispatcher: PostOffice,
                            ports: Map[String, Int], authenticator: IAuthenticator,
                            interceptor: BrokerNotifyInterceptor, readWriteControl: ReadWriteControl,
                            msgQueue: MsgQueue[IMessage]): Unit = {


    val sslCtxCreator: ISslContextCreator = new DefaultOctopusSslContextCreator(config)


    /**
     * 初始化参数
     */
    initialize(bossGroup, workerGroup,channelClass, config, msgDispatcher, sessionRegistry, ports, authenticator, interceptor, readWriteControl, msgQueue)

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



  private def createSslHandler(channel: SocketChannel, sslContext: SslContext, needsClientAuth: Boolean): ChannelHandler = {
    val sslEngine = sslContext.newEngine(channel.alloc, channel.remoteAddress.getHostString, channel.remoteAddress.getPort)
    sslEngine.setUseClientMode(false)
    if (needsClientAuth) sslEngine.setNeedClientAuth(true)
    new SslHandler(sslEngine)
  }

}
