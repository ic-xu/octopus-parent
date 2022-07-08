package io.octopus.scala.broker.mqtt.server.handler

import io.handler.codec.mqtt.MqttPublishMessage
import io.handler.codec.mqtt.utils.MqttMessageDecoderUtils
import io.netty.buffer.ByteBuf
import io.octopus.scala.broker.mqtt.server.PostOffice
import io.octopus.udp.message.MessageReceiverListener
import io.octopus.worker.MessageHandlerWorker

import java.lang

/**
 * @author chenxu
 * @version 1
 */

class UdpReceiverMessageHandler extends MessageReceiverListener {

  private val worker = new Array[MessageHandlerWorker](1)

  private var postOffice: PostOffice = _


  def this(postOffice: PostOffice) {
    this() //调用主构造函数
    this.postOffice = postOffice
    for (i <- worker.indices) {
      worker(i) = new MessageHandlerWorker(postOffice)
      worker(i).setDaemon(true)
      worker(i).setName("udp receiver processor " + i)
      worker(i).start()
    }
  }


  /**
   * 这个方法不能阻塞，应为只有这个方法调用成功之后，才会响应给对方消息收到了。
   * 如果阻塞，可能导致对方认为消息丢失以至于多次回掉这个消息。
   *
   * @param messageId 消息Id
   * @param msg       收到的消息
   * @return boolean
   */
  override def onMessage(messageId: lang.Long, msg: ByteBuf): lang.Boolean = {

    val decode = MqttMessageDecoderUtils.decode(messageId, msg.array)
    decode match {
      case publishMessage: MqttPublishMessage =>
        val i = publishMessage.variableHeader.topicName.hashCode
        val index = (worker.length - 1) % i
        worker(index).processMessage(publishMessage)
        true
      case _ => true
    }

  }
}
