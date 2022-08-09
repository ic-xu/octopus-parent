package io.octopus.scala.broker.mqtt.server.handler

import io.handler.codec.mqtt.MqttMessage
import io.netty.channel.ChannelHandler.Sharable
import io.netty.channel.{ChannelFutureListener, ChannelHandlerContext, ChannelInboundHandlerAdapter, DefaultChannelPromise}
import io.netty.util.ReferenceCountUtil
import io.octopus.broker.handler.InflictReSenderHandler
import io.octopus.kernel.utils.NettyUtils
import io.octopus.scala.broker.mqtt.factory.MQTTConnectionFactory
import io.octopus.scala.broker.mqtt.server.MQTTConnection
import io.octopus.utils.MqttMessageUtils
import org.slf4j.{Logger, LoggerFactory}

/**
 * @author chenxu
 * @version 1
 */
@Sharable
class NettyMQTTHandler(connectionFactory: MQTTConnectionFactory) extends ChannelInboundHandlerAdapter {

  val logger: Logger = LoggerFactory.getLogger(classOf[NettyMQTTHandler])

//  private val ATTR_CONNECTION = "connection"
//  private val ATTR_KEY_CONNECTION: AttributeKey[Object] = AttributeKey.valueOf(ATTR_CONNECTION)
//
//
//  private def bindMqttConnection(channel: Channel, connection: MQTTConnection): Unit = {
//    channel.attr(ATTR_KEY_CONNECTION).set(connection)
//  }
//
//  private def getMQTTConnection2Channel(channel: Channel) = channel.attr(ATTR_KEY_CONNECTION).get.asInstanceOf[MQTTConnection]


  override def channelRead(ctx: ChannelHandlerContext, message: Object): Unit = {
    var msg:MqttMessage = null
    //从channel 中获取相应的连接对象，通过连接对象处理相应的事务
    val mqttConnection: MQTTConnection = NettyUtils.getMQTTConnection2Channel(ctx.channel).asInstanceOf[MQTTConnection]
    try {
      msg = MqttMessageUtils.validateMessage(message)
      mqttConnection.handleMessage(msg)
    }
    catch {
      case ex: Throwable =>
        //ctx.fireExceptionCaught(ex);
        logger.error("Error processing protocol message: {}", msg.fixedHeader.messageType, ex)
        ctx.channel().close().addListener((_ : DefaultChannelPromise) => logger.info("Closed client channel due to exception in processing"))
    } finally ReferenceCountUtil.safeRelease(msg)
  }



  override def channelReadComplete(ctx: ChannelHandlerContext): Unit = {
    val mqttConnection: MQTTConnection = NettyUtils.getMQTTConnection2Channel(ctx.channel).asInstanceOf[MQTTConnection]
    mqttConnection.readCompleted()
  }

  override def channelActive(ctx: ChannelHandlerContext): Unit = { // 每次连接上来 使用连接工厂创建一个连接管理器
    val connection = connectionFactory.create(ctx.channel)
    //把相应的连接对象封装在channel中，以供后续使用
    NettyUtils.bindMqttConnection(ctx.channel, connection)
  }

  override def channelInactive(ctx: ChannelHandlerContext): Unit = {
    val mqttConnection: MQTTConnection = NettyUtils.getMQTTConnection2Channel(ctx.channel).asInstanceOf[MQTTConnection]
    mqttConnection.handleConnectionLost()
  }


  override def exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable): Unit = {
    logger.error("Unexpected exception while processing MQTT message. Closing Netty channel. CId={}", NettyUtils.clientID(ctx.channel), cause)
    ctx.close.addListener(ChannelFutureListener.CLOSE_ON_FAILURE)
  }

  override def channelWritabilityChanged(ctx: ChannelHandlerContext): Unit = {
    val mqttConnection: MQTTConnection = NettyUtils.getMQTTConnection2Channel(ctx.channel).asInstanceOf[MQTTConnection]
    mqttConnection.writabilityChanged()
    ctx.fireChannelWritabilityChanged
  }


  override def userEventTriggered(ctx: ChannelHandlerContext, evt: Object): Unit = {
    if (evt.isInstanceOf[InflictReSenderHandler.ResendNotAckedPublishes]) {
      val mqttConnection: MQTTConnection = NettyUtils.getMQTTConnection2Channel(ctx.channel).asInstanceOf[MQTTConnection]
      mqttConnection.reSendNotAckedPublishes()
    }
    ctx.fireUserEventTriggered(evt)
  }


}
