package io.octopus.broker.router
import io.handler.codec.mqtt.utils.MqttEncoderUtils
import io.handler.codec.mqtt.{MqttMessage, MqttMessageType, MqttPublishMessage}
import io.octopus.persistence.RouterRegister
import io.octopus.udp.message.MessageSendListener
import io.octopus.udp.sender.Sender

import java.net.InetSocketAddress
import java.util

class UdpRouter(val sender:Sender,val routerRegister: RouterRegister) extends RouteMessage2OtherBrokerServer{

  override def router(message: MqttMessage, messageSendListener: MessageSendListener): Unit = {
    message.fixedHeader().messageType() match {
      case MqttMessageType.PUBLISH => processPublishMessage(message.asInstanceOf[MqttPublishMessage],messageSendListener)
      case MqttMessageType.CUSTOMER =>
      case _=>
    }

  }

  private def processPublishMessage(message:MqttPublishMessage,messageSendListener: MessageSendListener):Unit={
    val inetSocketAddresses = searchTargetAddress(message.variableHeader.topicName)
    if (null != inetSocketAddresses)
      inetSocketAddresses.forEach((address: InetSocketAddress) =>
        sender.send(MqttEncoderUtils.decodeMessage(message), message.getMqttMessageTranceId, address, messageSendListener))
  }


  private def searchTargetAddress(topicName: String): util.Set[InetSocketAddress] = routerRegister.getAddressByTopic(topicName)
}
