package io.octopus.scala.broker.http

import io.netty.channel.EventLoopGroup
import io.netty.channel.socket.ServerSocketChannel
import io.netty.handler.codec.http.{HttpObjectAggregator, HttpServerCodec, HttpServerExpectContinueHandler}
import io.netty.handler.stream.ChunkedWriteHandler
import io.octopus.broker.handler.NettyHttpServerHandler
import io.octopus.kernel.kernel.config.IConfig
import io.octopus.kernel.kernel.contants.BrokerConstants
import io.octopus.kernel.kernel.interceptor.ConnectionNotifyInterceptor
import io.octopus.kernel.kernel.postoffice.IPostOffice
import io.octopus.kernel.kernel.security.{IAuthenticator, ReadWriteControl}
import io.octopus.kernel.kernel.session.ISessionResistor
import io.octopus.kernel.kernel.subscriptions.ISubscriptionsDirectory
import io.octopus.kernel.kernel.transport.BaseTransport
import org.slf4j.{Logger, LoggerFactory}

import java.util

/**
 * @author chenxu
 * @version 1
 */

class HttpTransport extends BaseTransport {

  private val logger: Logger = LoggerFactory.getLogger(classOf[HttpTransport])




  override def initProtocol(bossGroup: EventLoopGroup, workerGroup: EventLoopGroup, channelClass: Class[_ <: ServerSocketChannel], config: IConfig,
                            sessionRegistry: ISessionResistor, subscriptionsDirectory: ISubscriptionsDirectory,
                            msgDispatcher: IPostOffice, ports: java.util.Map[String, Integer], authenticator: IAuthenticator,
                            interceptor: util.List[ConnectionNotifyInterceptor], readWriteControl: ReadWriteControl): Unit = {

    initialize(bossGroup, workerGroup,channelClass, config, msgDispatcher, sessionRegistry, ports, authenticator, interceptor, readWriteControl)


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
