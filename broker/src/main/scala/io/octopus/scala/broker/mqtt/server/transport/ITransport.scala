package io.octopus.scala.broker.mqtt.server.transport

import io.netty.channel.EventLoopGroup
import io.netty.channel.socket.ServerSocketChannel
import io.octopus.broker.security.ReadWriteControl
import io.octopus.kernel.kernel.config.IConfig
import io.octopus.kernel.kernel.interceptor.NotifyInterceptor
import io.octopus.kernel.kernel.security.IAuthenticator
import io.octopus.kernel.kernel.session.ISessionResistor
import io.octopus.kernel.kernel.subscriptions.ISubscriptionsDirectory
import io.octopus.scala.broker.mqtt.server.PostOffice

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
                   config: IConfig, sessionRegistry: ISessionResistor,
                   subscriptionsDirectory: ISubscriptionsDirectory,
                   msgDispatcher: PostOffice,
                   ports: Map[String, Int], authenticator: IAuthenticator,
                   interceptor: NotifyInterceptor, readWriteControl: ReadWriteControl): Unit

}
