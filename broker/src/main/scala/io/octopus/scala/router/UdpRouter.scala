package io.octopus.scala.router

import io.handler.codec.mqtt.utils.MessageEncoderUtils
import io.handler.codec.mqtt.{MqttMessage, MqttMessageType, MqttPublishMessage}
import io.octopus.base.interfaces.IRouterRegister
import io.octopus.router.RouteMessage2OtherBrokerServer
import io.octopus.udp.message.MessageSendListener
import io.octopus.udp.sender.Sender

import java.net.InetSocketAddress
import java.util

class UdpRouter(val sender: Sender, val routerRegister: IRouterRegister) extends RouteMessage2OtherBrokerServer {

  override def router(message: MqttMessage, messageSendListener: MessageSendListener): Unit = {
    message.fixedHeader().messageType() match {
      case MqttMessageType.PUBLISH => processPublishMessage(message.asInstanceOf[MqttPublishMessage], messageSendListener)
      case MqttMessageType.CUSTOMER =>
      case _ =>
    }

  }

  private def processPublishMessage(message: MqttPublishMessage, messageSendListener: MessageSendListener): Unit = {
    val inetSocketAddresses = searchTargetAddress(message.variableHeader.topicName)
    if (null != inetSocketAddresses) {
      val inetIterator = inetSocketAddresses.iterator()
      while (inetIterator.hasNext) {
        sender.send(MessageEncoderUtils.decodeMessage(message), message.getMessageId, inetIterator.next(), messageSendListener)
      }
    }
  }


  private def searchTargetAddress(topicName: String): util.Set[InetSocketAddress] = routerRegister.getAddressByTopic(topicName)
}
