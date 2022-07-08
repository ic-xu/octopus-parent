package io.octopus.scala.broker.mqtt.server.handler

import io.netty.channel.ChannelHandler.Sharable
import io.netty.channel.{ChannelHandlerContext, ChannelInboundHandlerAdapter}


/**
 * beforeInterceptor
 * Implement message processing interception before th message is processed
 *
 * @author chenxu
 */
@Sharable
class BeforeInterceptorHandler extends ChannelInboundHandlerAdapter {

  override def channelRead(ctx: ChannelHandlerContext, msg: Any): Unit = {
    super.channelRead(ctx, msg)
  }

}
