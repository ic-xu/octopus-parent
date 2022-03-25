package io.octopus.scala.broker.transport

import io.handler.codec.mqtt.IMessage
import io.netty.channel.EventLoopGroup
import io.netty.channel.socket.ServerSocketChannel
import io.octopus.base.config.IConfig
import io.octopus.base.interfaces.IAuthenticator
import io.octopus.base.queue.MsgQueue
import io.octopus.base.subscriptions.ISubscriptionsDirectory
import io.octopus.broker.security.ReadWriteControl
import io.octopus.interception.BrokerNotifyInterceptor
import io.octopus.scala.broker.PostOffice
import io.octopus.scala.broker.session.SessionResistor

/**
 * @author chenxu
 * @version 1
 */

trait ITransport {

  /**
   * 初始化协议
   *
   * @param bossGroup bossGroup
   * @param workerGroup workerGroup
   * @param config config
   * @param sessionRegistry sessionRegistry
   * @param subscriptionsDirectory subscriptionsDirectory
   * @param msgDispatcher msgDispatcher
   * @param ports ports
   * @param authenticator authenticator
   * @param interceptor interceptor
   * @param readWriteControl readWriteControl
   * @param msgQueue msgQueue
   */
  def initProtocol(bossGroup: EventLoopGroup, workerGroup: EventLoopGroup, channelClass: Class[_ <: ServerSocketChannel],
                   config: IConfig, sessionRegistry: SessionResistor,
                   subscriptionsDirectory: ISubscriptionsDirectory,
                   msgDispatcher: PostOffice,
                   ports: Map[String, Int], authenticator: IAuthenticator,
                   interceptor: BrokerNotifyInterceptor, readWriteControl: ReadWriteControl,
                   msgQueue: MsgQueue[IMessage]): Unit

}
