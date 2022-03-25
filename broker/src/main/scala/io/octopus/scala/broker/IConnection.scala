package io.octopus.scala.broker

import io.handler.codec.mqtt.IMessage
import io.netty.channel.Channel

import java.net.InetSocketAddress


/**
 * @author chenxu
 * @version 1
 */

trait IConnection {


  /**
   * received
   */
  def received():Unit

  /**
   * publishMessage
   * @param publishMsg message
   */
  def sendPublish(publishMsg: IMessage): Unit

  def sendIfWritableElseDrop(msg: IMessage): Unit

  def sendPublishReceived(messageID: Int): Unit

  def receiverQos2(msg: IMessage, clientId: String, username: String, messageId: Int): Unit

  /**
   * connection lost
   */
  def handleConnectionLost(): Unit


  /**
   * dropConnection
   */
  def dropConnection(): Unit

  /**
   * getUsername
   *
   * @return
   */
  def getUsername: String


  def getClientId: String


  def getChannel: Channel

  /**
   * readCompleted
   */
  def readCompleted():Unit

  /**
   * flush Channel
   */
  def flush(): Unit


  /**
   * connection remoteAddress
   * @return
   */
  def remoteAddress: InetSocketAddress


  /**
   * reSendNotAckedPublishes
   */
  def reSendNotAckedPublishes(): Unit



}
