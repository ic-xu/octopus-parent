package io.octopus.scala.broker.mqtt.server.listener

import io.netty.channel.{ChannelFuture, ChannelFutureListener}
import io.octopus.scala.broker.mqtt.server.TransportBootstrap
import org.slf4j.LoggerFactory

import java.net.InetSocketAddress

/**
 * @author chenxu
 * @date 2022/1/6 7:07 下午
 * @version 1
 */
/**
 * LocalPortReaderFutureListener
 *
 * @param transportName transportName eg：tcp udp wss
 */
class BindLocalPortListener(transportName: String, var ports: Map[String, Int]) extends ChannelFutureListener {

  private val logger = LoggerFactory.getLogger(classOf[TransportBootstrap])

  override def operationComplete(f: ChannelFuture): Unit = {
    if (f.isSuccess) {
      val localAddress = f.channel().localAddress()
      localAddress match {
        case inetAddress: InetSocketAddress =>
          logger.debug("bound {} port: {}", transportName, inetAddress.getPort)
          val port = inetAddress.getPort
          ports += (transportName -> port)
        case _ =>
      }
    }
  }
}