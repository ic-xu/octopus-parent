package io.octopus.scala.broker.mqtt.server.handler

import com.codahale.metrics.{Counter, Meter, MetricRegistry}
import io.handler.codec.mqtt.{MqttMessage, MqttMessageType}
import io.netty.channel.ChannelHandler.Sharable
import io.netty.channel.{ChannelHandlerContext, ChannelInboundHandlerAdapter}
import io.octopus.config.IConfig
import io.octopus.kernel.kernel.IPostOffice
import io.octopus.kernel.kernel.metrics.MetricsPubMessageReport
import io.octopus.kernel.utils.NettyUtils

import java.util.concurrent.TimeUnit

/**
 * @author chenxu
 * @version 1
 */

@Sharable
class DropWizardMetricsHandler(postOffice: IPostOffice) extends ChannelInboundHandlerAdapter {


  private var metrics: MetricRegistry = _
  private var publishesMetrics: Meter = _
  private var subscribeMetrics: Meter = _
  private var connectedClientsMetrics: Counter = _


  def init(config: IConfig): Unit = {
    this.metrics = new MetricRegistry
    this.publishesMetrics = metrics.meter("publish.requests")
    this.subscribeMetrics = metrics.meter("subscribe.requests")
    this.connectedClientsMetrics = metrics.counter("connect.num_clients")
    val metricsPubMessageReport = MetricsPubMessageReport
      .forRegistry(metrics)
      .convertRatesTo(TimeUnit.SECONDS)
      .convertDurationsTo(TimeUnit.MILLISECONDS)
      .build(postOffice)
    metricsPubMessageReport.start(1, TimeUnit.MINUTES)
  }


  override def channelRead(ctx: ChannelHandlerContext, message: Object): Unit = {
    val msg = message.asInstanceOf[MqttMessage]
    val messageType = msg.fixedHeader.messageType
    messageType match {
      case MqttMessageType.PUBLISH =>
        this.publishesMetrics.mark()

      case MqttMessageType.SUBSCRIBE =>
        this.subscribeMetrics.mark()

      case MqttMessageType.CONNECT =>
        this.connectedClientsMetrics.inc()

      case MqttMessageType.DISCONNECT =>
      //                this.connectedClientsMetrics.dec();

      case _ =>

    }
    ctx.fireChannelRead(message)
  }

  override def channelInactive(ctx: ChannelHandlerContext): Unit = {
    val clientID = NettyUtils.clientID(ctx.channel)
    if (clientID != null && clientID.nonEmpty) this.connectedClientsMetrics.dec()
    ctx.fireChannelInactive
  }

}
