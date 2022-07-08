package io.octopus.scala.broker.mqtt.server.handler

import com.alibaba.fastjson.JSON
import io.handler.codec.mqtt._
import io.netty.buffer.{ByteBuf, Unpooled}
import io.octopus.broker.customer.MqttCustomerMessageType
import io.octopus.kernel.kernel.session.ISessionResistor
import io.octopus.scala.broker.mqtt.casep.{ChatMessage, SdpMessage}
import io.octopus.scala.broker.mqtt.server.MQTTConnection
import org.slf4j.{Logger, LoggerFactory}

object CustomerHandler {

  val LOGGER: Logger = LoggerFactory.getLogger(this.getClass)

  def processMessage(message: MqttCustomerMessage, connect: MQTTConnection, sessionRegister: ISessionResistor): Unit = {
    message.getMessageType match {

      case MqttCustomerMessageType.LIST_CLIENT_IDS => processListClientIdsMessage(connect, sessionRegister)

      case MqttCustomerMessageType.OFFER => processSdpMessage(message, connect, sessionRegister, MqttCustomerMessageType.OFFER)

      case MqttCustomerMessageType.ANSWER => processSdpMessage(message, connect, sessionRegister, MqttCustomerMessageType.ANSWER)

      case MqttCustomerMessageType.CHAT => processChatMessage(message, connect, sessionRegister)

      case _ =>

    }
  }


  /**
   * get all clientId
   *
   * @param connect        connect
   * @param sessionFactory sessionRegister
   */
  private def processListClientIdsMessage(connect: MQTTConnection, sessionFactory: ISessionResistor): Unit = {
    val clientIdSet = sessionFactory.getAllClientId
//    connect.boundSession.sendCustomerMessage(wrapperCustomerMessage(connect.nextPacketId, wrapperByteBuf(JSON.toJSONString(clientIdSet)), MqttCustomerMessageType.LIST_CLIENT_IDS))
  }


  private def processChatMessage(message: MqttCustomerMessage, connect: MQTTConnection, sessionRegister: ISessionResistor): Unit = {
    val chatMessage: ChatMessage = JSON.parseObject(message.payload().array(), classOf[ChatMessage])
    val session = sessionRegister.retrieve(chatMessage.userId)
    if (null == session) {
      return
    }
    chatMessage.setUserId(connect.getUsername)
    val messageString: String = JSON.toJSONString(chatMessage)
//    session.sendCustomerMessage(wrapperCustomerMessage(connect.nextPacketId, wrapperByteBuf(messageString), MqttCustomerMessageType.CHAT))
  }


  private def processSdpMessage(message: MqttCustomerMessage, connect: MQTTConnection, sessionRegister: ISessionResistor, customerMessageType: Byte): Unit = {
    val sdpMessage: SdpMessage = JSON.parseObject(message.payload().array(), classOf[SdpMessage])
    val session = sessionRegister.retrieve(sdpMessage.clientId)
//    session.sendCustomerMessage(wrapperCustomerMessage(connect.nextPacketId, wrapperByteBuf(sdpMessage.sdp), customerMessageType))
  }

  private def wrapperByteBuf(objects: String): ByteBuf = {
    Unpooled.wrappedBuffer(objects.getBytes("UTF-8"))
  }


//  private def wrapperCustomerMessage(packageId: Int, payload: ByteBuf, customerMessageType: Byte): Message = {
//     new Message(new SimpleId(0L,packageId),MsgQos.AT_MOST_ONCE, MsgRouteType.TOPIC,"",payload,false)
////    new MqttCustomerMessage(new MqttFixedHeader(MqttMessageType.CUSTOMER, false, MqttQoS.AT_MOST_ONCE, false, payload.readableBytes),
////      new MqttCustomerVariableHeader(packageId), payload, customerMessageType)
//  }

}
