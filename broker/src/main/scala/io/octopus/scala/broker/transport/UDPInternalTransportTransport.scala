package io.octopus.scala.broker.transport

import io.handler.codec.mqtt.IMessage
import io.netty.bootstrap.Bootstrap
import io.netty.buffer.ByteBuf
import io.netty.channel.socket.ServerSocketChannel
import io.netty.channel.socket.nio.NioDatagramChannel
import io.netty.channel.{AdaptiveRecvByteBufAllocator, ChannelOption, EventLoopGroup}
import io.octopus.base.config.IConfig
import io.octopus.base.contants.BrokerConstants
import io.octopus.base.interfaces.IAuthenticator
import io.octopus.base.queue.MsgQueue
import io.octopus.base.subscriptions.ISubscriptionsDirectory
import io.octopus.broker.security.ReadWriteControl
import io.octopus.interception.BrokerNotifyInterceptor
import io.octopus.scala.broker.PostOffice
import io.octopus.scala.broker.handler.UdpReceiverMessageHandler
import io.octopus.scala.broker.session.SessionResistor
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
                            config: IConfig, sessionRegistry: SessionResistor,
                            subscriptionsDirectory: ISubscriptionsDirectory,
                            msgDispatcher: PostOffice, ports: Map[String, Int],
                            authenticator: IAuthenticator,
                            interceptor: BrokerNotifyInterceptor,
                            readWriteControl: ReadWriteControl, msgQueue: MsgQueue[IMessage]): Unit = {


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
    sessionRegistry.setUdpChannel(bootstrap.channel)
  }
}
