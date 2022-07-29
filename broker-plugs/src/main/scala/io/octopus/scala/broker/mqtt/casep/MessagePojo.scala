package io.octopus.scala.broker.mqtt.casep

/**
 *
 * 1 byte ,message type(1:login,2:publish,)
 * 1 -> clientId,
 * 2 -> topic,messageBody
 *
 */
case class UdpPublishMessage(topic: String, body: String)

/**
 * sdp 消息
 *
 * @param sdp      交换消息
 * @param clientId 客户端id
 */
case class SdpMessage(clientId: String, sdp: String)
