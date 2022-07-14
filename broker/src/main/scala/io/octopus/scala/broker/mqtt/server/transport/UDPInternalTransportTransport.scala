package io.octopus.scala.broker.mqtt.server.transport

import io.netty.bootstrap.Bootstrap
import io.netty.buffer.ByteBuf
import io.netty.channel.socket.ServerSocketChannel
import io.netty.channel.socket.nio.NioDatagramChannel
import io.netty.channel.{AdaptiveRecvByteBufAllocator, ChannelOption, EventLoopGroup}
import io.octopus.kernel.kernel.config.IConfig
import io.octopus.kernel.kernel.contants.BrokerConstants
import io.octopus.kernel.kernel.interceptor.ConnectionNotifyInterceptor
import io.octopus.kernel.kernel.postoffice.IPostOffice
import io.octopus.kernel.kernel.security.{IAuthenticator, ReadWriteControl}
import io.octopus.kernel.kernel.session.ISessionResistor
import io.octopus.kernel.kernel.subscriptions.ISubscriptionsDirectory
import io.octopus.kernel.kernel.transport.ITransport
import io.octopus.scala.broker.mqtt.server.handler.UdpReceiverMessageHandler
import io.octopus.udp.config.TransportConfig
import io.octopus.udp.message.DelayMessage
import io.octopus.udp.receiver.netty.handler.NettyUdpDecoderServerHandler
import org.slf4j.{Logger, LoggerFactory}

import java.util.concurrent.locks.ReentrantLock
import java.util.concurrent.{ConcurrentHashMap, DelayQueue}

/**
 * @author chenxu
 * @version 1
 */

class UDPInternalTransportTransport extends ITransport {

  private val logger: Logger = LoggerFactory.getLogger(classOf[UDPInternalTransportTransport])
  private val reentrantLock = new ReentrantLock
  /*
* udp 相关参数
*/
  private val messageCache: ConcurrentHashMap[java.lang.Long, Array[ByteBuf]] = new ConcurrentHashMap[java.lang.Long, Array[ByteBuf]](128)
  private val delayMessageQueue = new DelayQueue[DelayMessage]


  override def initProtocol(bossGroup: EventLoopGroup, workerGroup: EventLoopGroup, channelClass: Class[_ <: ServerSocketChannel],
                            config: IConfig, sessionResistor: ISessionResistor,
                            subscriptionsDirectory: ISubscriptionsDirectory,
                            msgDispatcher: IPostOffice, ports: java.util.Map[String, Integer],
                            authenticator: IAuthenticator,
                            interceptor: java.util.List[ConnectionNotifyInterceptor],
                            readWriteControl: ReadWriteControl): Unit = {


    logger.debug("Configuring UDP Internal MQTT transport")
    val host = config.getProperty(BrokerConstants.HOST_PROPERTY_NAME)
    val udpPort = config.getIntegerProperty(BrokerConstants.UDP_PORT_PROPERTY_NAME, BrokerConstants.UDP_TRANSPORT_DEFAULT_PORT)
    val bootstrap = new Bootstrap()
      .group(workerGroup)
      .channel(classOf[NioDatagramChannel])
      .option(ChannelOption.SO_BROADCAST, boolean2Boolean(true))
      .option(ChannelOption.RCVBUF_ALLOCATOR, new AdaptiveRecvByteBufAllocator(64, 1024, 100 * 65536))
      .handler(new NettyUdpDecoderServerHandler(reentrantLock, new TransportConfig, messageCache, delayMessageQueue, new UdpReceiverMessageHandler(msgDispatcher)))
      .bind(udpPort).sync
    logger.info("Server bound to host={}, port={}, protocol={}", host, udpPort, "UDP")
    //TODO  sessionResistor.setUdpChannel(bootstrap.channel)
//    sessionResistor.setUdpChannel(bootstrap.channel)
  }
}
