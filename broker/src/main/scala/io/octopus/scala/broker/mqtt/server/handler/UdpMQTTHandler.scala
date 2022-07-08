package io.octopus.scala.broker.mqtt.server.handler

import com.alibaba.fastjson.JSON
import io.netty.buffer.Unpooled
import io.netty.channel.socket.DatagramPacket
import io.netty.channel.{ChannelHandlerContext, SimpleChannelInboundHandler}
import io.octopus.kernel.kernel.session.ISessionResistor
import io.octopus.kernel.kernel.subscriptions.{ISubscriptionsDirectory, Topic}
import io.octopus.scala.broker.mqtt.casep.UdpPublishMessage

import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets


class UdpMQTTHandler(sessionRegistry: ISessionResistor, subscriptionsDirectory: ISubscriptionsDirectory)
  extends SimpleChannelInboundHandler[DatagramPacket] {

  val messageType: Array[Byte] = Array(2)


  override def channelRead0(channelHandlerContext: ChannelHandlerContext, i: DatagramPacket): Unit = {

    i.content().readByte() match {
      case 1 => bindSession(i.sender(), i.content().toString(StandardCharsets.UTF_8))
      case 2 => publishMessage(channelHandlerContext, i.content().toString(StandardCharsets.UTF_8))
    }
  }


  def bindSession(address: InetSocketAddress, clientId: String): Unit = {
    val session = sessionRegistry.retrieve(clientId)
    if (null != session) {
      session.bindUdpInetSocketAddress(address)
    }
  }


  def publishMessage(channelHandlerContext: ChannelHandlerContext, bodyString: String): Unit = {
    try {
      val message: UdpPublishMessage = JSON.parseObject(bodyString, classOf[UdpPublishMessage])
      val subscriptionsSet = subscriptionsDirectory.matchQosSharpening(new Topic(message.topic), false)
      val subscriptionsSetIterator = subscriptionsSet.iterator()
      while (subscriptionsSetIterator.hasNext) {
        val subscription = subscriptionsSetIterator.next()
        val session = sessionRegistry.retrieve(subscription.getClientId)
        if (null != session && null != session.getUdpInetSocketAddress) {
          channelHandlerContext.writeAndFlush(new DatagramPacket(Unpooled.wrappedBuffer(messageType,
            bodyString.getBytes(StandardCharsets.UTF_8)), session.getUdpInetSocketAddress))
        }
      }
    } catch {
      case e: Exception =>
        println(bodyString)
        println(e.getMessage)
    }

  }


}
