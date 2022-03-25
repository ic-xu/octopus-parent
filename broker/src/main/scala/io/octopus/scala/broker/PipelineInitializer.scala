package io.octopus.scala.broker

import io.netty.channel.socket.SocketChannel

/**
 * @author chenxu
 * @vrsion 1
 */
trait PipelineInitializer {

  /*
   * config channel Pipeline
   */
  def init(channel:SocketChannel)

}
