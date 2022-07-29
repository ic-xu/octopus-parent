package io.octopus.scala.broker.mqtt.utils

import io.handler.codec.mqtt.MqttPublishMessage
import io.octopus.kernel.kernel.message.{KernelPayloadMessage, PubEnum, MsgQos, MsgRouter, PacketIPackageId}

/**
 * @author chenxu
 * @date 2022/1/27 10:48 上午
 * @version 1
 */

object MqttPublishMsg2Message {

  def mqttPublishMessage2Message(mqttPublishMessage: MqttPublishMessage):KernelPayloadMessage={
    val qos = MsgQos.valueOf(mqttPublishMessage.fixedHeader().qosLevel().value())
    val topicStr = mqttPublishMessage.variableHeader().topicName()
    new KernelPayloadMessage(mqttPublishMessage.variableHeader().packetId().toShort,
      qos,MsgRouter.TOPIC,topicStr,mqttPublishMessage.payload(),mqttPublishMessage.fixedHeader().isRetain,PubEnum.PUBLISH)
  }

}
