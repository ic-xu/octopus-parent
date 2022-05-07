package io.octopus.scala.broker.handler

import com.alibaba.fastjson.JSON
import io.handler.codec.mqtt._
import io.netty.buffer.{ByteBuf, Unpooled}
import io.octopus.broker.customer.MqttCustomerMessageType
import io.octopus.scala.broker.MQTTConnection
import io.octopus.scala.broker.handler.signalling.SignallingMsg
import io.octopus.scala.broker.session.SessionResistor
import io.octopus.scala.casep.{ChatMessage, SdpMessage}
import org.slf4j.{Logger, LoggerFactory}

/**
 *
 */
object CustomerHandler {

  val LOGGER: Logger = LoggerFactory.getLogger(this.getClass)

  

  def processMessage(message: MqttCustomerMessage, connect: MQTTConnection, sessionRegister: SessionResistor): Unit = {
    message.getMessageType match {

      // 信息令牌服务器
      case MqttCustomerMessageType.SIGNALLING => processSignallingMsg(JSON.parseObject(message.payload().toString(), classOf[SignallingMsg]))

      case MqttCustomerMessageType.LIST_CLIENT_IDS => processListClientIdsMessage(connect, sessionRegister)

      case MqttCustomerMessageType.OFFER => processSdpMessage(message, connect, sessionRegister, MqttCustomerMessageType.OFFER)

      case MqttCustomerMessageType.ANSWER => processSdpMessage(message, connect, sessionRegister, MqttCustomerMessageType.ANSWER)

      case MqttCustomerMessageType.CHAT => processChatMessage(message, connect, sessionRegister)

      case _ =>

    }
  }


  private def processSignallingMsg(msg: SignallingMsg): Unit = {
    println(msg)
  }

  /**
   * get all clientId
   *
   * @param connect        connect
   * @param sessionFactory sessionRegister
   */
  private def processListClientIdsMessage(connect: MQTTConnection, sessionFactory: SessionResistor): Unit = {
    val clientIdSet = sessionFactory.getAllClientId
    connect.boundSession.sendCustomerMessage(wrapperCustomerMessage(connect.boundSession.nextPacketId(), wrapperByteBuf(JSON.toJSONString(clientIdSet)), MqttCustomerMessageType.LIST_CLIENT_IDS))
  }


  private def processChatMessage(message: MqttCustomerMessage, connect: MQTTConnection, sessionRegister: SessionResistor): Unit = {
    val chatMessage: ChatMessage = JSON.parseObject(message.payload().array(), classOf[ChatMessage])
    val session = sessionRegister.retrieve(chatMessage.userId)
    if (null == session) {
      return
    }
    chatMessage.setUserId(connect.getUsername)
    val messageString: String = JSON.toJSONString(chatMessage)
    session.sendCustomerMessage(wrapperCustomerMessage(connect.boundSession.nextPacketId(), wrapperByteBuf(messageString), MqttCustomerMessageType.CHAT))
  }


  private def processSdpMessage(message: MqttCustomerMessage, connect: MQTTConnection, sessionRegister: SessionResistor, customerMessageType: Byte): Unit = {
    val sdpMessage: SdpMessage = JSON.parseObject(message.payload().array(), classOf[SdpMessage])
    val session = sessionRegister.retrieve(sdpMessage.clientId)
    session.sendCustomerMessage(wrapperCustomerMessage(connect.boundSession.nextPacketId(), wrapperByteBuf(sdpMessage.sdp), customerMessageType))
  }

  private def wrapperByteBuf(str: String): ByteBuf = {
    Unpooled.wrappedBuffer(str.getBytes("UTF-8"))
  }


  private def wrapperCustomerMessage(packageId: Int, payload: ByteBuf, customerMessageType: Byte): MqttCustomerMessage =
    new MqttCustomerMessage(new MqttFixedHeader(MqttMessageType.CUSTOMER, false, MqttQoS.AT_MOST_ONCE, false, payload.readableBytes),
      new MqttCustomerVariableHeader(packageId), payload, customerMessageType)

}
