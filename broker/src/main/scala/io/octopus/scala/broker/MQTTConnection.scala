package io.octopus.scala.broker

import io.handler.codec.mqtt._
import io.netty.channel.{Channel, ChannelFuture, ChannelFutureListener}
import io.netty.handler.timeout.IdleStateHandler
import io.netty.util.ReferenceCountUtil
import io.octopus.base.config.BrokerConfiguration
import io.octopus.base.interfaces.IAuthenticator
import io.octopus.base.queue.{MsgQueue, StoreMsg}
import io.octopus.base.subscriptions.Topic
import io.octopus.base.utils.{DebugUtils, NettyUtils, ObjectUtils}
import io.octopus.broker.exception.SessionCorruptedException
import io.octopus.broker.handler.InflictReSenderHandler
import io.octopus.broker.security.ReadWriteControl
import io.octopus.broker.session.CreationModeEnum
import io.octopus.interception.BrokerNotifyInterceptor
import io.octopus.scala.broker.handler.CustomerHandler
import io.octopus.scala.broker.session.{Session, SessionCreationResult, SessionResistor}
import org.slf4j.{Logger, LoggerFactory}

import java.net.InetSocketAddress
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * connection
 *
 * @param channel        netty channel
 * @param brokerConfig   config
 * @param authenticator  auther
 * @param sessionFactory sessionRegister
 * @param postOffice     postOffice
 * @param interceptor    interceptor
 */
class MQTTConnection(channel: Channel, brokerConfig: BrokerConfiguration, authenticator: IAuthenticator,
                     sessionFactory: SessionResistor, postOffice: PostOffice,
                     authorizator: ReadWriteControl, msgQueue: MsgQueue[IMessage], interceptor: BrokerNotifyInterceptor)
  extends IConnection {


  val logger: Logger = LoggerFactory.getLogger(classOf[MQTTConnection])

  var boundSession: Session = _
  private val connected: AtomicBoolean = new AtomicBoolean(false)




  /**
   * process message
   *
   * @param msg mqttMessage
   */
  def handleMessage(msg: MqttMessage): Unit = {
    val messageType = msg.fixedHeader.messageType
    logger.debug("Received MQTT message, type: {}, channel: {}", messageType, channel)
    messageType match {
      case MqttMessageType.CUSTOMER => processCustomer(msg.asInstanceOf[MqttCustomerMessage])
      case MqttMessageType.CONNECT => processConnect(msg.asInstanceOf[MqttConnectMessage])
      case MqttMessageType.CONNACK => processConnectAck(msg.asInstanceOf[MqttConnAckMessage])
      case MqttMessageType.PUBLISH => processPublish(msg.asInstanceOf[MqttPublishMessage])
      case MqttMessageType.PUBACK => processPubAck(msg)
      case MqttMessageType.PUBREC => processPubRec(msg)
      case MqttMessageType.PUBREL => processPubRel(msg)
      case MqttMessageType.PUBCOMP => processPubComp(msg)
      case MqttMessageType.SUBSCRIBE => processSubscribe(msg.asInstanceOf[MqttSubscribeMessage])
      case MqttMessageType.SUBACK => processSubAck(msg.asInstanceOf[MqttSubAckMessage])
      case MqttMessageType.UNSUBSCRIBE => processUnsubscribe(msg.asInstanceOf[MqttUnsubscribeMessage])
      case MqttMessageType.UNSUBACK => processUnSubAck(msg)
      case MqttMessageType.PINGREQ => processPing(msg)
      case MqttMessageType.PINGRESP => processPingResp(msg)
      case MqttMessageType.DISCONNECT => processDisconnect(msg)
      case MqttMessageType.AUTH => processAuth(msg)
      case _ =>
        logger.error("Unknown MessageType: {}, channel: {}", messageType, channel)
    }
  }

  /**
   * processCustomer message,the message type is @Link MqttMessageType.CUSTOMER
   *
   * @param msg message
   */
  private def processCustomer(msg: MqttCustomerMessage): Unit = {
    CustomerHandler.processMessage(msg, null, sessionFactory)
  }

  private def processConnect(msg: MqttConnectMessage): Unit = {
    val payload = msg.payload
    var clientId = payload.clientIdentifier
    val username = payload.userName
    logger.trace("Processing CONNECT message. CId={} username: {} channel: {}", clientId, username, channel)

    //1 、 check mqtt proto version
    if (isNotProtocolVersion(msg, MqttVersion.MQTT_3_1) && isNotProtocolVersion(msg, MqttVersion.MQTT_3_1_1) && isNotProtocolVersion(msg, MqttVersion.MQTT_2)) {
      logger.warn("MQTT protocol version is not valid. CId={} channel: {}", clientId, channel)
      abortConnection(MqttConnectReturnCode.CONNECTION_REFUSED_UNACCEPTABLE_PROTOCOL_VERSION)
      return
    }
    val cleanSession = msg.variableHeader.isCleanSession
    // 2、 check clientId
    if (clientId == null || clientId.isEmpty) {
      if (!brokerConfig.isAllowZeroByteClientId) {
        logger.info("Broker doesn't permit MQTT empty client ID. Username: {}, channel: {}", username, channel)
        abortConnection(MqttConnectReturnCode.CONNECTION_REFUSED_IDENTIFIER_REJECTED)
        return
      }
      if (!cleanSession) {
        logger.info("MQTT client ID cannot be empty for persistent session. Username: {}, channel: {}", username, channel)
        abortConnection(MqttConnectReturnCode.CONNECTION_REFUSED_IDENTIFIER_REJECTED)
        return
      }
      // Generating client id.
      clientId = UUID.randomUUID.toString.replace("-", "")
      logger.debug("Client has connected with integration generated id: {}, username: {}, channel: {}", clientId, username, channel)
    }
    // 3 、 check user and password
    if (!login(msg, clientId)) {
      abortConnection(MqttConnectReturnCode.CONNECTION_REFUSED_BAD_USER_NAME_OR_PASSWORD)
      channel.close.addListener(ChannelFutureListener.CLOSE_ON_FAILURE)
      return
    }
    // 4、 create a session for the connection
    var result: SessionCreationResult = null
    try {
      logger.trace("Binding MQTTConnection (channel: {}) to session", channel)
      result = sessionFactory.createOrReOpenSession(msg, this, clientId, this.getUsername)
      result.getSession.bind(this)
      boundSession = result.getSession
    } catch {
      case sessionException: SessionCorruptedException =>
        logger.error(sessionException.getMessage)
        logger.warn("MQTT session for client ID {} cannot be created, channel: {}", clientId, channel)
        abortConnection(MqttConnectReturnCode.CONNECTION_REFUSED_SERVER_UNAVAILABLE)
        return
    }

    val isSessionAlreadyPresent = !cleanSession && result.getAlreadyStored
    val clientIdUsed = clientId
    val ackMessage = MqttMessageBuilders.connAck.returnCode(MqttConnectReturnCode.CONNECTION_ACCEPTED).sessionPresent(isSessionAlreadyPresent).build
    channel.writeAndFlush(ackMessage).addListener(new ChannelFutureListener() {
      override def operationComplete(future: ChannelFuture): Unit = {
        if (future.isSuccess) {
          logger.trace("CONN - ACK sent, channel: {}", channel)
          if (!result.getSession.completeConnection) { //change the session status
            // send DISCONNECT and close the channel
            val disconnectMsg = MqttMessageBuilders.disconnect.build
            channel.writeAndFlush(disconnectMsg).addListener(ChannelFutureListener.CLOSE)
            logger.warn("CONN - ACK is sent but the session created can't transition in CONNECTED state")
          }
          else { //clientIdUsed == clientId
            NettyUtils.clientID(channel, clientIdUsed)
            // 连接标记设置为true
            connected.set(true)
            // OK continue with sending queued messages and normal flow
            //notify other offline
            if (result.getMode eq CreationModeEnum.REOPEN_EXISTING) result.getSession.sendQueuedMessagesWhileOffline()
            initializeKeepAliveTimeout(channel, msg, clientIdUsed)

            //  set InflictReSender
            channel.pipeline.addFirst("inflictReSender", new InflictReSenderHandler(5_000, TimeUnit.MILLISECONDS))
            postOffice.dispatchConnection(msg)
            logger.trace("dispatch connection: {}", msg)
          }
        }
        else {
          boundSession.disconnect()
          boundSession.cleanSessionQueue()
          sessionFactory.remove(boundSession)
          logger.error("CONNACK send failed, cleanup session and close the connection", future.cause)
          channel.close()
        }

      }
    })

  }

  private def processConnectAck(message: MqttConnAckMessage): Unit = {}

  private def processPublish(message: MqttPublishMessage): Unit = {
    val qos = message.fixedHeader.qosLevel
    val username = NettyUtils.userName(channel)
    val topicName = message.variableHeader.topicName
    val clientId = getClientId
    logger.debug("Processing PUBLISH message. CId={}, topic: {}, messageId: {}, qos: {}", clientId, topicName, message.variableHeader.packetId, qos)
    val topic = new Topic(topicName)
    if (!topic.isValid) {
      logger.debug("Drop connection because of invalid topic format")
      dropConnection()
    }

    qos match {
      case MqttQoS.AT_MOST_ONCE =>

        postOffice.receivedPublishQos0(topic, username, clientId, message)

      case MqttQoS.AT_LEAST_ONCE =>
        //write Disk
        val result = receivedQos1(this, topic, username, message)
        if (!ObjectUtils.isEmpty(result)) postOffice.publishMessage(result)

      case MqttQoS.EXACTLY_ONCE =>
        receiverPublishQos2(message, username)

      case _ =>
        logger.error("Unknown QoS-Type:{}", qos)

    }
  }

  private def processPubAck(message: MqttMessage): Unit = {
    val messageId = message.variableHeader.asInstanceOf[MqttMessageIdVariableHeader].messageId
    boundSession.pubAckReceived(messageId)
  }

  private def processPubRec(message: MqttMessage): Unit = {
    boundSession.processPubRec(message)
  }

  private def processPubRel(message: MqttMessage): Unit = {

    val messageId = message.variableHeader.asInstanceOf[MqttMessageIdVariableHeader].messageId
    boundSession.receivedPubRelQos2(messageId)
  }

  private def processPubComp(message: MqttMessage): Unit = {

    val messageID = message.variableHeader.asInstanceOf[MqttMessageIdVariableHeader].messageId
    boundSession.processPubComp(messageID)
  }

  private def processSubscribe(message: MqttSubscribeMessage): Unit = {
    val clientID = NettyUtils.clientID(channel)
    if (!connected.get()) {
      logger.warn("SUBSCRIBE received on already closed connection, CId={}, channel: {}", clientID, channel)
      dropConnection()
      return
    }
    postOffice.subscribeClientToTopics(message, clientID, NettyUtils.userName(channel), this)
  }

  private def processSubAck(message: MqttSubAckMessage): Unit = {}

  /**
   * unsubscribe
   *
   * @param message message
   */
  private def processUnsubscribe(message: MqttUnsubscribeMessage): Unit = {
    val topics = message.payload.topics
    val clientID = NettyUtils.clientID(channel)

    logger.trace("Processing UNSUBSCRIBE message. CId={}, topics: {}", clientID, topics)
    postOffice.unsubscribe(topics, this, message.variableHeader.messageId)
  }

  private def processUnSubAck(msg: MqttMessage): Unit = {}

  private def processPing(msg: MqttMessage): Unit = {

    val pingHeader = new MqttFixedHeader(MqttMessageType.PINGRESP, false, MqttQoS.AT_MOST_ONCE, false, 0)
    val pingResp = new MqttMessage(pingHeader)
    channel.writeAndFlush(pingResp).addListener(ChannelFutureListener.CLOSE_ON_FAILURE)
  }

  private def processPingResp(msg: MqttMessage): Unit = {}

  private def processDisconnect(msg: MqttMessage): Unit = {
    logger.trace(msg.toString)
    val clientID = NettyUtils.clientID(channel)
    logger.trace("Start DISCONNECT CIInFlight(this)d={}, channel: {}", clientID, channel)
    if (!connected.get()) {
      logger.info("DISCONNECT received on already closed connection, CId={}, channel: {}", clientID, channel)
      return
    }
    boundSession.disconnect()
    connected.set(false)
    channel.close.addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE)
    logger.trace("Processed DISCONNECT CId={}, channel: {}", clientID, channel)
    val userName = NettyUtils.userName(channel)
    postOffice.dispatchDisconnection(clientID, userName)
    logger.trace("dispatch disconnection: clientId={}, userName={}", clientID, userName)
  }

  private def processAuth(msg: MqttMessage): Unit = {}

  def handleConnectionLost(): Unit = {
    import io.octopus.base.utils.NettyUtils
    val clientID = NettyUtils.clientID(channel)
    val userName = NettyUtils.userName(channel)
    if (clientID == null || clientID.isEmpty) return
    logger.trace("Notifying connection lost event. CId: {}, channel: {}", clientID, channel)
    if (boundSession.hasWill) postOffice.fireWill(boundSession.getWill, this)
    if (boundSession.isClean) {
      logger.debug("Remove session for client CId: {}, channel: {}", clientID, channel)
      /*
                   *清除用户注册信息
                   */ boundSession.cleanSessionQueue
      postOffice.cleanSubscribe(boundSession.getSubscriptions)
      sessionFactory.removeUserClientIdByUsername(userName, clientID)
      sessionFactory.remove(boundSession)
    }
    else boundSession.disconnect()
    connected.set(false)
    //dispatch connection lost to intercept.
    postOffice.dispatchConnectionLost(clientID, userName)
    logger.trace("dispatch disconnection: clientId={}, userName={}", clientID, userName)

  }

  def getSessionFactory: SessionResistor = sessionFactory

  def getPostOffice: PostOffice = postOffice


  private def isNotProtocolVersion(msg: MqttConnectMessage, version: MqttVersion) = msg.variableHeader.version != version.protocolLevel

  /**
   * getUsername
   *
   * @return
   */
  def getUsername: String = NettyUtils.userName(channel)


  /**
   * abortConnect
   *
   * @param returnCode error code
   */
  private def abortConnection(returnCode: MqttConnectReturnCode): Unit = {
    val badProto = MqttMessageBuilders.connAck.returnCode(returnCode).sessionPresent(false).build
    channel.writeAndFlush(badProto).addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE)
    channel.close.addListener(ChannelFutureListener.CLOSE_ON_FAILURE)
  }

  /**
   * login user
   *
   * @param msg      connectMsg
   * @param clientId client
   * @return
   */
  private def login(msg: MqttConnectMessage, clientId: String): Boolean = { // handle user authentication
    if (msg.variableHeader.hasUserName) {
      var pwd: Array[Byte] = null
      if (msg.variableHeader.hasPassword) pwd = msg.payload.passwordInBytes
      else if (!brokerConfig.isAllowAnonymous) {
        logger.info("Client didn't supply any password and MQTT anonymous mode is disabled CId={}", clientId)
        return false
      }
      val userNameString = msg.payload.userName
      if (!authenticator.checkUsername(clientId, userNameString, pwd)) {
        logger.info("Authenticator has rejected the MQTT credentials CId={}, username={}", clientId, userNameString)
        return false
      }
      NettyUtils.userName(channel, userNameString)
      sessionFactory.registerUserName(userNameString, clientId)
    }
    else if (!brokerConfig.isAllowAnonymous) {
      logger.info("Client didn't supply any credentials and MQTT anonymous mode is disabled. CId={}", clientId)
      return false
    }
    true
  }

  /**
   * init timeout
   *
   * @param channel  c
   * @param msg      msg
   * @param clientId clientId
   */
  private def initializeKeepAliveTimeout(channel: Channel, msg: MqttConnectMessage, clientId: String): Unit = {
    val keepAlive = msg.variableHeader.keepAliveTimeSeconds
    NettyUtils.keepAlive(channel, keepAlive)
    NettyUtils.cleanSession(channel, msg.variableHeader.isCleanSession)
    NettyUtils.clientID(channel, clientId)
    val idleTime = keepAlive * 1.5f.round
    if (channel.pipeline().names.contains("idleStateHandler"))
      channel.pipeline().remove("idleStateHandler")
    channel.pipeline().addFirst("idleStateHandler", new IdleStateHandler(idleTime, 0, 0))
    logger.debug("Connection has been configured CId={}, keepAlive={}, removeTemporaryQoS2={}, idleTime={}",
      clientId, keepAlive, msg.variableHeader.isCleanSession, idleTime)
  }

  def dropConnection(): Unit = {
    channel.close.addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE)
  }

  def getClientId: String = NettyUtils.clientID(channel)


  /**
   * write qos1 message to Disk
   *
   * @param topic    topic
   * @param username username
   * @param msg      msg
   */
  def receivedQos1(connection: MQTTConnection, topic: Topic, username: String, msg: MqttPublishMessage): StoreMsg[IMessage] = {
    // verify if topic can be write
    if (!topic.isValid) {
      logger.warn("Invalid topic format, force close the connection")
      connection.dropConnection()
      return null
    }
    val clientId = connection.getClientId
    if (!authorizator.canWrite(topic, username, clientId)) {
      logger.error("MQTT client: {} is not authorized to publish on topic: {}", clientId, topic)
      return null
    }
    val offerResult = msgQueue.offer(msg)
    if (!ObjectUtils.isEmpty(offerResult)) {
      connection.sendPubAck(msg.variableHeader.packetId)
      interceptor.notifyTopicPublished(msg, clientId, username)
    }
    offerResult
  }

  /**
   * add update
   *
   * @param mqttPublishMessage msg
   * @param username           username
   */
  private def receiverPublishQos2(mqttPublishMessage: MqttPublishMessage, username: String): Unit = {
    logger.trace("Processing PUBREL message on connection: {}", this)
    //发布
    val topic = new Topic(mqttPublishMessage.variableHeader.topicName)
    val clientId = getClientId
    if (!authorizator.canWrite(topic, username, clientId)) {
      logger.error("MQTT client is not authorized to publish on topic. CId={}, topic: {}", clientId, topic)
      //TODO  notify client ,can not to write.
    }
    else { //回复消息
      boundSession.sendPublishReceivedQos2(mqttPublishMessage)
    }
  }

  /**
   * First phase of a publish QoS2 protocol, sent by publisher to the broker. Publish to all interested
   * subscribers.
   */
  def receiverQos2(msg: IMessage, clientId: String, username: String, messageId: Int): Unit = {
    msg match {
      case mqttPublishMessage: MqttPublishMessage =>
        val offerResult = msgQueue.offer(mqttPublishMessage)
        if (!ObjectUtils.isEmpty(offerResult)) {
          interceptor.notifyTopicPublished(mqttPublishMessage, clientId, username)
          sendPubCompMessage(messageId)
          postOffice.publishMessage(offerResult)
        }
    }
  }


  def sendPublishReceived(messageID: Int): Unit = {
    logger.trace("sendPubRec invoked on channel: {}", channel)
    val fixedHeader = new MqttFixedHeader(MqttMessageType.PUBREC, false, MqttQoS.AT_MOST_ONCE, false, 0)
    val pubRecMessage = new MqttPubRecMessage(fixedHeader, MqttMessageIdVariableHeader.from(messageID))
    sendIfWritableElseDrop(pubRecMessage)
  }


  def sendSubAckMessage(messageID: Int, ackMessage: MqttSubAckMessage): Unit = {
    val clientId = NettyUtils.clientID(channel)
    logger.trace("Sending SUBACK response CId={}, messageId: {}", clientId, messageID)
    channel.writeAndFlush(ackMessage).addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE)
  }


  def sendUnSubAckMessage(topics: java.util.List[String], clientID: String, messageID: Int): Unit = {
    val fixedHeader = new MqttFixedHeader(MqttMessageType.UNSUBACK, false, MqttQoS.AT_MOST_ONCE, false, 0)
    val ackMessage = new MqttUnsubAckMessage(fixedHeader, MqttMessageIdVariableHeader.from(messageID))
    logger.trace("Sending UNSUBACK message. CId={}, messageId: {}, topics: {}", clientID, messageID, topics)
    channel.writeAndFlush(ackMessage).addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE)
    logger.trace("Client <{}> unsubscribed from topics <{}>", clientID, topics)
  }

  def sendPublish(msg: IMessage): Unit = {
    msg match {
      case publishMsg: MqttPublishMessage =>
        val packetId = publishMsg.variableHeader.packetId
        val topicName = publishMsg.variableHeader.topicName
        val clientId = getClientId
        val qos = publishMsg.fixedHeader.qosLevel
        if (logger.isTraceEnabled) logger.trace("Sending PUBLISH({}) message. MessageId={}, CId={}, topic={}, payload={}",
          qos, packetId, clientId, topicName, DebugUtils.payload2Str(publishMsg.payload))
        else logger.debug("Sending PUBLISH({}) message. MessageId={}, CId={}, topic={}", qos, packetId, clientId, topicName)
        sendIfWritableElseDrop(publishMsg)
    }
  }


  def sendPubAck(messageID: Int): Unit = {
    logger.trace("sendPubAck invoked")
    val fixedHeader = new MqttFixedHeader(MqttMessageType.PUBACK, false, MqttQoS.AT_MOST_ONCE, false, 0)
    val pubAckMessage = new MqttPubAckMessage(fixedHeader, MqttMessageIdVariableHeader.from(messageID))
    sendIfWritableElseDrop(pubAckMessage)
  }


  def sendPubCompMessage(messageID: Int): Unit = {
    logger.trace("Sending PUBCOMP message on channel: {}, messageId: {}", channel, messageID)
    val fixedHeader = new MqttFixedHeader(MqttMessageType.PUBCOMP, false, MqttQoS.AT_MOST_ONCE, false, 0)
    val pubCompMessage = new MqttPubCompMessage(fixedHeader, MqttMessageIdVariableHeader.from(messageID))
    sendIfWritableElseDrop(pubCompMessage)
  }

  def sendIfWritableElseDrop(message: IMessage): Unit = {
    message match {
      case msg: MqttMessage =>
        logger.trace("write mqttMessage packageId is {}", msg.variableHeader)
        if (logger.isDebugEnabled) logger.debug("OUT {} on channel {}", msg.fixedHeader.messageType, channel)
        if (channel.isWritable) {
          var channelFuture: ChannelFuture = null
          if (brokerConfig.isImmediateBufferFlush) {
            channelFuture = channel.writeAndFlush(msg)
          }
          else channelFuture = channel.write(msg)
          channelFuture.addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE)
        }
        else {
          ReferenceCountUtil.release(msg.payload)
          // TODO
          //            LOGGER.error("channel is not writable");
        }
    }
  }


  def getChannel: Channel = channel




  def readCompleted(): Unit = {
    logger.debug("readCompleted client CId: {}, channel: {}", getClientId, channel)
    if (getClientId != null) { // TODO drain all messages in target's session in-flight message queue
      boundSession.flushAllQueuedMessages()
    }
  }

  def flush(): Unit = {
    channel.flush
  }


  def remoteAddress: InetSocketAddress = channel.remoteAddress.asInstanceOf[InetSocketAddress]


  def writabilityChanged(): Unit = {
    if (channel.isWritable) {
      logger.debug("Channel {} is again writable", channel)
      boundSession.writeAbilityChanged()
    }
  }

  def reSendNotAckedPublishes(): Unit = {
    boundSession.reSendInflictNotAcked()
  }

  /**
   * received
   */
  override def received(): Unit = {

  }

}
