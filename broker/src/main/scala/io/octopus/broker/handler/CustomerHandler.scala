package io.octopus.broker.handler

import com.alibaba.fastjson.JSON
import io.handler.codec.mqtt.{MqttCustomerMessage, MqttCustomerVariableHeader, MqttFixedHeader, MqttMessageType, MqttQoS}
import io.netty.buffer.{ByteBuf, Unpooled}
import io.octopus.broker.{MqttConnection, SessionRegistry}
import io.octopus.broker.customer.MqttCustomerMessageType
import org.slf4j.{Logger, LoggerFactory}

object CustomerHandler {

  val LOGGER: Logger = LoggerFactory.getLogger(this.getClass)

  def processMessage(message: MqttCustomerMessage, connect: MqttConnection, sessionRegistry: SessionRegistry): Unit = {
    message.getMessageType match {

      case MqttCustomerMessageType.listClientIds => processListClientIdsMessage(connect, sessionRegistry)

      case MqttCustomerMessageType.offer => processSdpMessage(message, connect, sessionRegistry, MqttCustomerMessageType.offer)

      case MqttCustomerMessageType.answer => processSdpMessage(message, connect, sessionRegistry, MqttCustomerMessageType.answer)

      case MqttCustomerMessageType.chat => processChatMessage(message, connect, sessionRegistry)

      case _ =>

    }
  }


  /**
   * get all clientId
   *
   * @param connect         connect
   * @param sessionRegistry sessionRegister
   */
  private def processListClientIdsMessage(connect: MqttConnection, sessionRegistry: SessionRegistry): Unit = {
    val clientIdSet = sessionRegistry.getAllClientId
    connect.getBoundSession.sendCustomerMessage(wrapperCustomerMessage(connect.nextPacketId(), wrapperByteBuf(JSON.toJSONString(clientIdSet)), MqttCustomerMessageType.listClientIds))
  }


  private def processChatMessage(message: MqttCustomerMessage, connect: MqttConnection, sessionRegistry: SessionRegistry): Unit = {
    val chatMessage: ChatMessage = JSON.parseObject(message.payload().array(), classOf[ChatMessage])
    val session = sessionRegistry.retrieve(chatMessage.userId)
    if (null == session) {
      return
    }
    chatMessage.setUserId(connect.getUsername)
   val messageString:String = JSON.toJSONString(chatMessage)
    session.sendCustomerMessage(wrapperCustomerMessage(connect.nextPacketId(), wrapperByteBuf(messageString), MqttCustomerMessageType.chat))
  }


  private def processSdpMessage(message: MqttCustomerMessage, connect: MqttConnection, sessionRegistry: SessionRegistry, customerMessageType: Byte): Unit = {
    val sdpMessage: SdpMessage = JSON.parseObject(message.payload().array(), classOf[SdpMessage])
    val session = sessionRegistry.retrieve(sdpMessage.clientId)
    session.sendCustomerMessage(wrapperCustomerMessage(connect.nextPacketId(), wrapperByteBuf(sdpMessage.sdp), customerMessageType))
  }

  private def wrapperByteBuf(objects: String): ByteBuf = {
    Unpooled.wrappedBuffer(objects.getBytes("UTF-8"))
  }


  private def wrapperCustomerMessage(packageId: Int, payload: ByteBuf, customerMessageType: Byte): MqttCustomerMessage =
    new MqttCustomerMessage(new MqttFixedHeader(MqttMessageType.CUSTOMER, false, MqttQoS.AT_MOST_ONCE, false, payload.readableBytes),
      new MqttCustomerVariableHeader(packageId), payload, customerMessageType)

}
