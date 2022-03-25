package io.octopus.scala.broker.transport

import io.handler.codec.mqtt.IMessage
import io.netty.channel.EventLoopGroup
import io.netty.channel.socket.ServerSocketChannel
import io.netty.handler.codec.http.{HttpObjectAggregator, HttpServerCodec, HttpServerExpectContinueHandler}
import io.netty.handler.stream.ChunkedWriteHandler
import io.octopus.base.config.IConfig
import io.octopus.base.contants.BrokerConstants
import io.octopus.base.interfaces.IAuthenticator
import io.octopus.base.queue.MsgQueue
import io.octopus.base.subscriptions.ISubscriptionsDirectory
import io.octopus.broker.handler.NettyHttpServerHandler
import io.octopus.broker.security.ReadWriteControl
import io.octopus.interception.BrokerNotifyInterceptor
import io.octopus.scala.broker.PostOffice
import io.octopus.scala.broker.session.SessionResistor
import org.slf4j.{Logger, LoggerFactory}

/**
 * @author chenxu
 * @version 1
 */

class HttpTransport extends BaseTransport {

  private val logger: Logger = LoggerFactory.getLogger(classOf[HttpTransport])




  override def initProtocol(bossGroup: EventLoopGroup, workerGroup: EventLoopGroup, channelClass: Class[_ <: ServerSocketChannel], config: IConfig,
                            sessionRegistry: SessionResistor, subscriptionsDirectory: ISubscriptionsDirectory,
                            msgDispatcher: PostOffice, ports: Map[String, Int], authenticator: IAuthenticator,
                            interceptor: BrokerNotifyInterceptor, readWriteControl: ReadWriteControl,
                            msgQueue: MsgQueue[IMessage]): Unit = {

    initialize(bossGroup, workerGroup,channelClass, config, msgDispatcher, sessionRegistry, ports, authenticator, interceptor, readWriteControl, msgQueue)


    logger.debug("Configuring HTTP transport")
    val httpPortProp = config.getProperty(BrokerConstants.HTTP_PORT, "8090")
    val port = httpPortProp.toInt
    val host = config.getProperty(BrokerConstants.HOST_PROPERTY_NAME)
    initTcpTransportFactory(host, port, "HTTP", channel => {

      val p = channel.pipeline
      /*
                   * 或者使用HttpRequestDecoder & HttpResponseEncoder
                   */
      p.addLast(new HttpServerCodec)
      /*
                   * 在处理POST消息体时需要加上
                   */
      p.addLast(new HttpObjectAggregator(10 * 1024 * 1024))
      p.addLast(new HttpServerExpectContinueHandler)
      p.addLast(new ChunkedWriteHandler)
      p.addLast(new NettyHttpServerHandler(sessionRegistry, subscriptionsDirectory))

    })
  }
}
