package io.octopus.scala.broker.mqtt.server.transport

import io.netty.bootstrap.Bootstrap
import io.netty.channel.socket.ServerSocketChannel
import io.netty.channel.socket.nio.NioDatagramChannel
import io.netty.channel.{AdaptiveRecvByteBufAllocator, ChannelOption, EventLoopGroup}
import io.octopus.broker.security.ReadWriteControl
import io.octopus.kernel.kernel.config.IConfig
import io.octopus.kernel.kernel.contants.BrokerConstants
import io.octopus.kernel.kernel.interceptor.NotifyInterceptor
import io.octopus.kernel.kernel.security.IAuthenticator
import io.octopus.kernel.kernel.session.ISessionResistor
import io.octopus.kernel.kernel.subscriptions.ISubscriptionsDirectory
import io.octopus.scala.broker.mqtt.server.PostOffice
import io.octopus.scala.broker.mqtt.server.handler.UdpMQTTHandler
import org.slf4j.{Logger, LoggerFactory}

/**
 * @author chenxu
 * @version 1
 */
class UDPTransportTransport extends ITransport {

  private val logger: Logger = LoggerFactory.getLogger(classOf[UDPTransportTransport])



  override def initProtocol(bossGroup: EventLoopGroup, workerGroup: EventLoopGroup, channelClass: Class[_ <: ServerSocketChannel],
                            config: IConfig, sessionRegistry: ISessionResistor,
                            subscriptionsDirectory: ISubscriptionsDirectory,
                            msgDispatcher: PostOffice, ports: Map[String, Int],
                            authenticator: IAuthenticator, interceptor: NotifyInterceptor,
                            readWriteControl: ReadWriteControl): Unit = {


    logger.debug("Configuring UDP MQTT transport")
    val host = config.getProperty(BrokerConstants.HOST_PROPERTY_NAME)
    val portProp = config.getIntegerProperty(BrokerConstants.PORT_PROPERTY_NAME, 1883)
    val bootstrap = new Bootstrap()
      .group(workerGroup)
      .channel(classOf[NioDatagramChannel])
      .option(ChannelOption.SO_BROADCAST, boolean2Boolean(true))
      .option(ChannelOption.RCVBUF_ALLOCATOR, new AdaptiveRecvByteBufAllocator(64, 1024, 100 * 65536))
      .handler(new UdpMQTTHandler(sessionRegistry, subscriptionsDirectory))
      .bind(portProp)
      .sync
    logger.info("Server bound to host={}, port={}, protocol={}", host, portProp, "UDP")
//    sessionRegistry.setUdpChannel(bootstrap.channel)

  }
}
