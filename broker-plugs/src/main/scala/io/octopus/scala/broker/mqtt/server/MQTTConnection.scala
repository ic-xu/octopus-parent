package io.octopus.scala.broker.mqtt.server

import io.handler.codec.mqtt._
import io.netty.buffer.Unpooled
import io.netty.channel.{Channel, ChannelFuture, ChannelFutureListener}
import io.netty.handler.timeout.IdleStateHandler
import io.netty.util.ReferenceCountUtil
import io.octopus.broker.handler.InflictReSenderHandler
import io.octopus.config.BrokerConfiguration
import io.octopus.kernel.exception.SessionCorruptedException
import io.octopus.kernel.kernel._
import io.octopus.kernel.kernel.connect.AbstractConnection
import io.octopus.kernel.kernel.interceptor.ConnectionNotifyInterceptor
import io.octopus.kernel.kernel.message._
import io.octopus.kernel.kernel.security.IAuthenticator
import io.octopus.kernel.kernel.subscriptions.{Subscription, Topic}
import io.octopus.kernel.utils.{NettyUtils, ObjectUtils}
import io.octopus.scala.broker.mqtt.server.handler.CustomerHandler
import io.octopus.utils.{DebugUtils, SubscriptionUtils}
import org.slf4j.{Logger, LoggerFactory}

import java.util
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * connection
 *
 * @param channel         netty channel
 * @param brokerConfig    config
 * @param sessionResistor sessionRegister
 * @param interceptor     interceptor
 */
class MQTTConnection(channel: Channel, brokerConfig: BrokerConfiguration, authenticator: IAuthenticator,
                     sessionResistor: ISessionResistor, interceptors: java.util.List[ConnectionNotifyInterceptor])
  extends AbstractConnection(channel, brokerConfig, authenticator, sessionResistor, interceptors) {

  var publishMsgQos2: Option[MqttPublishMessage] = Option.empty
  val logger: Logger = LoggerFactory.getLogger(classOf[MQTTConnection])
  //  private val qos2Receiving: util.Map[Integer, MqttPublishMessage] = new util.HashMap[Integer, MqttPublishMessage]
  var boundSession: DefaultSession = _
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
      /// 发布确认
      case MqttMessageType.PUBACK => processPubAck(msg)
      ///  发布收到（QoS 2，第二步）
      case MqttMessageType.PUBREC => processPubRec(msg.asInstanceOf[MqttPubRecMessage])
      // PUBREL – 发布释放（QoS 2，第三步）
      case MqttMessageType.PUBREL => processPubRel(msg.asInstanceOf[MqttPubRelMessage])
      /// PUBCOMP – 发布完成（QoS 2，第四步）
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
    CustomerHandler.processMessage(msg, this, sessionResistor)
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
      result = sessionResistor.createOrReOpenSession(clientId, this.getUsername, msg.variableHeader().isCleanSession, createWillMsg(msg), msg.variableHeader().version())
      boundSession = result.getSession
      boundSession.bind(this)
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
            if (result.getMode eq CreationModeEnum.REOPEN_EXISTING) {
              result.getSession.sendQueuedMessagesWhileOffline()
            }
            initializeKeepAliveTimeout(channel, msg, clientIdUsed)

            //  set InflictReSender
            channel.pipeline.addFirst("inflictReSender", new InflictReSenderHandler(5_000, TimeUnit.MILLISECONDS))
            //            boundSession.handleConnectionLost(channel)

            //                        postOffice.dispatchConnection(msg)
            logger.trace("dispatch connection: {}", msg)
          }
        }
        else {
          boundSession.handleConnectionLost()
          boundSession.cleanSessionQueue()
          sessionResistor.remove(boundSession)
          logger.error("CONNACK send failed, cleanup session and close the connection", future.cause)
          channel.close()
        }

      }
    })

  }


  /**
   * create will msg
   *
   * @return
   */

  def createWillMsg(msg: MqttConnectMessage): KernelPayloadMessage = {
    if (!msg.variableHeader().isWillFlag) {
      return null
    }
    val willPayload = Unpooled.copiedBuffer(msg.payload.willMessageInBytes)
    val willTopic = msg.payload.willTopic
    val retained = msg.variableHeader.isWillRetain
    val qos = MsgQos.valueOf(msg.variableHeader().willQos())
    new KernelPayloadMessage(3.toShort, qos, MsgRouter.TOPIC, willTopic, willPayload, retained, PubEnum.PUBLISH)
  }

  private def processConnectAck(msg: MqttConnAckMessage): Unit = {}

  private def processPublish(msg: MqttPublishMessage): Unit = {
    val qos = msg.fixedHeader.qosLevel
    val topicName = msg.variableHeader.topicName
    val clientId = getClientId
    logger.debug("Processing PUBLISH message. CId={}, topic: {}, messageId: {}, qos: {}", clientId, topicName, msg.variableHeader.packetId, qos)
    val topic = new Topic(topicName)
    if (!topic.isValid) {
      logger.debug("Drop connection because of invalid topic format")
      dropConnection()
    }
    qos match {
      case MqttQoS.AT_MOST_ONCE =>
        val receiverMsg = new KernelPayloadMessage(msg.variableHeader().packetId().toShort, MsgRouter.TOPIC, topicName, msg.payload(), msg.fixedHeader().isRetain)
        receiverMsg.setQos(MsgQos.AT_MOST_ONCE)
        boundSession.receiveMsg(receiverMsg)
      case MqttQoS.AT_LEAST_ONCE =>
        val receiverMsg = new KernelPayloadMessage(msg.variableHeader().packetId().toShort, MsgRouter.TOPIC, topicName, msg.payload(), msg.fixedHeader().isRetain)
        receiverMsg.setQos(MsgQos.AT_LEAST_ONCE)

        if (boundSession.receiveMsg(receiverMsg)) {
          sendPubAck(msg.variableHeader.packetId)
        }
      case MqttQoS.EXACTLY_ONCE =>
        logger.trace("Processing PUBREL message on connection: {}", this)
        val packageId = msg.variableHeader.packetId
        //        val old = qos2Receiving.put(packageId, msg.copy)
        // In case of evil client with duplicate msgid.
        //        if (null != old) {
        //          ReferenceCountUtil.safeRelease(old)
        //        }
        publishMsgQos2 = Option.apply(msg.copy())
        sendPublishReceived(packageId)

      case _ =>
        logger.error("Unknown QoS-Type:{}", qos)

    }
  }

  /**
   * 发布确认
   *
   * @param message msg
   */
  private def processPubAck(message: MqttMessage): Unit = {
    val shortId = message.variableHeader.asInstanceOf[MqttMessageIdVariableHeader].messageId
    boundSession.receivePubAcK(shortId.toShort)
  }


  /**
   * 发布收到（QoS 2，第二步）
   *
   * @param msg msg
   */
  private def processPubRec(msg: MqttPubRecMessage): Unit = {
    boundSession.receivePubRec(msg.variableHeader().messageId().toShort)
//    val mqttPubRelMessage = MqttMessageBuilders.pubRel().packetId(msg.variableHeader().messageId().toShort).build()
//    sendIfWritableElseDrop(mqttPubRelMessage)
  }


  /**
   * PUBREL – 发布释放（QoS 2，第三步）
   *
   * @param msg msg
   */
  private def processPubRel(msg: MqttPubRelMessage): Unit = {

    val messageId = msg.variableHeader.messageId
    val oldPublishMsg = publishMsgQos2.orNull
    if (null != oldPublishMsg) {
      val receiverMessage = new KernelPayloadMessage(msg.variableHeader().messageId().toShort, MsgQos.EXACTLY_ONCE, MsgRouter.TOPIC, oldPublishMsg.variableHeader().topicName(),
        oldPublishMsg.payload(), oldPublishMsg.fixedHeader().isRetain, PubEnum.PUBLISH)
      if (boundSession.receiveMsg(receiverMessage)) {
        //          interceptor.notifyTopicPublished(mqttPublishMessage, clientId, username)
        sendPubCompMessage(messageId)
      }
      //      receiverQos2(removedMsg, clientId, connection.getUsername, messageId)
      //      ReferenceCountUtil.safeRelease(removedMsg.payload)
    }
  }

  /**
   * PUBCOMP – 发布完成（QoS 2，第四步）
   *
   * @param message msg
   */
  private def processPubComp(message: MqttMessage): Unit = {

    val messageID = message.variableHeader.asInstanceOf[MqttMessageIdVariableHeader].messageId
    boundSession.receivePubComp(messageID.toShort)
  }

  private def processSubscribe(message: MqttSubscribeMessage): Unit = {
    val messageId = message.variableHeader().messageId()
    val clientID = NettyUtils.clientID(channel)
    if (!connected.get()) {
      logger.warn("SUBSCRIBE received on already closed connection, CId={}, channel: {}", clientID, channel)
      dropConnection()
      return
    }
    val subscriptions = SubscriptionUtils.wrapperSubscription(clientID, message)
    val successSubscriptions = boundSession.subscriptions(subscriptions)
    // 发送订阅成功的主题给到客户端
    sendSubAckMessage(message.variableHeader().messageId(), doAckMessageFromValidateFilters(successSubscriptions, messageId))
  }


  /**
   * Create the subAck response from a list of topicFilters
   */
  private def doAckMessageFromValidateFilters(subscriptions: util.List[Subscription], messageId: Int) = {
    val grantedQoSLevels = new util.ArrayList[Integer]
    subscriptions.forEach(req => {
      grantedQoSLevels.add(req.getRequestedQos.getValue)
    })
    val fixedHeader = new MqttFixedHeader(MqttMessageType.SUBACK, false, MqttQoS.AT_MOST_ONCE, false, 0)
    val payload = new MqttSubAckPayload(grantedQoSLevels)
    new MqttSubAckMessage(fixedHeader, MqttMessageIdVariableHeader.from(messageId), payload)
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
    boundSession.unSubscriptions(new util.HashSet[String](topics))
    sendUnSubAckMessage(topics, clientID, message.variableHeader().messageId())
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
    connected.set(false)
    channel.close.addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE)
    logger.trace("Processed DISCONNECT CId={}, channel: {}", clientID, channel)
    val userName = NettyUtils.userName(channel)
    handleConnectionLost()
    if (!ObjectUtils.isEmpty(interceptors)) {
      interceptors.forEach(interceptor => interceptor.notifyClientConnectionLost(boundSession.getClientId, boundSession.getUsername))
    }
    logger.trace("dispatch disconnection: clientId={}, userName={}", clientID, userName)
  }

  private def processAuth(msg: MqttMessage): Unit = {}


  def handleConnectionLost(): Unit = {
    val clientID = NettyUtils.clientID(channel)
    val userName = NettyUtils.userName(channel)
    if (clientID == null || clientID.isEmpty) return
    logger.trace("Notifying connection lost event. CId: {}, channel: {}", clientID, channel)

    /**
     * 取消订阅
     */
    if (boundSession.isClean) {
      logger.debug("Remove session for client CId: {}, channel: {}", clientID, channel)
      /*
                   *清除用户注册信息
                   */
      boundSession.cleanSessionQueue()
      boundSession.cleanSubscribe()
      sessionResistor.unRegisterClientIdByUsername(userName, clientID)
      sessionResistor.remove(boundSession)
    }
    connected.set(false)
    //dispatch connection lost to intercept.
    boundSession.handleConnectionLost()

    logger.trace("dispatch disconnection: clientId={}, userName={}", clientID, userName)

  }

  def getSessionFactory: ISessionResistor = sessionResistor


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
      if (!authenticator.checkValid(clientId, userNameString, pwd)) {
        logger.info("Authenticator has rejected the MQTT credentials CId={}, username={}", clientId, userNameString)
        return false
      }
      NettyUtils.userName(channel, userNameString)
      sessionResistor.registerUserName(userNameString, clientId)
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
   * 发送 消息质量为qos2 的消息后，发送一个确认消息
   *
   * @param messageID 消息id
   */
  def sendPublishReceived(messageID: Int): Unit = {
    logger.trace("sendPubRec invoked on channel: {}", channel)
    val fixedHeader = new MqttFixedHeader(MqttMessageType.PUBREC, false, MqttQoS.AT_MOST_ONCE, false, 0)
    val pubRecMessage = new MqttPubRecMessage(fixedHeader, MqttMessageIdVariableHeader.from(messageID))
    sendIfWritableElseDrop(pubRecMessage)
  }

  /**
   * 发送订阅成功消息收到确认
   *
   * @param messageID  消息id
   * @param ackMessage 发送消息
   */
  def sendSubAckMessage(messageID: Int, ackMessage: MqttSubAckMessage): Unit = {
    val clientId = NettyUtils.clientID(channel)
    logger.trace("Sending SUBACK response CId={}, messageId: {}", clientId, messageID)
    channel.writeAndFlush(ackMessage).addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE)
  }

  /**
   *
   * 发送取消订阅确认
   *
   * @param topics    topic
   * @param clientID  clientId
   * @param messageID messageId
   */
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

  /**
   * 发送消息收到确认，这个是收到qos1消息的第二个报文。
   *
   * @param messageID 消息id
   */
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

  /**
   * 发送MQTT消息，私有方法，只能在当前对象中调用
   *
   * @param mqttMsg 消息
   */
  private def sendIfWritableElseDrop(mqttMsg: MqttMessage): Unit = {
    logger.trace("write mqttMessage packageId is {}", mqttMsg.variableHeader)
    if (logger.isDebugEnabled) logger.debug("OUT {} on channel {}", mqttMsg.fixedHeader.messageType, channel)
    if (channel.isWritable) {
      var channelFuture: ChannelFuture = null
      if (brokerConfig.isImmediateBufferFlush) {
        channelFuture = channel.writeAndFlush(mqttMsg)
      }
      else channelFuture = channel.write(mqttMsg)
      channelFuture.addListener(ChannelFutureListener.CLOSE_ON_FAILURE)
    }
    else {
      //如果不能写，就释放消息
      ReferenceCountUtil.release(mqttMsg.payload)
    }
  }


  def readCompleted(): Unit = {
    logger.debug("readCompleted client CId: {}, channel: {}", getClientId, channel)
    if (getClientId != null) {
      /// drain all messages in target's session in-flight message queue
      boundSession.flushAllQueuedMessages()
    }
  }


  def writabilityChanged(): Unit = {
    if (channel.isWritable) {
      logger.debug("Channel {} is again writable", channel)
      boundSession.writeAbilityChanged()
    }
  }

  def reSendNotAckedPublishes(): Unit = {
    boundSession.reSendInflictNotAcked()
  }


  def sendIfWritableElseDrop(kernelMsg: KernelMessage): java.lang.Boolean = {
    logger.trace("write mqttMessage packageId is {}", kernelMsg.packageId())

    var mqttMsg: MqttMessage = null
    kernelMsg match {
      case msg: KernelPayloadMessage =>
        if (logger.isDebugEnabled) logger.debug("OUT {} on channel {}", msg.getTopic, channel)
        mqttMsg = MqttMessageBuilders.publish()
          .messageId(msg.packageId().intValue())
          .payload(msg.getPayload.retain).topicName(msg.getTopic)
          .qos(MqttQoS.valueOf(msg.getQos.getValue)).retained(false).build()
      case _ =>
        kernelMsg.getPubEnum match {
          case PubEnum.PUB_ACK => mqttMsg = MqttMessageBuilders.pubAck().packetId(kernelMsg.packageId()).build();
          case PubEnum.PUB_REC => mqttMsg = MqttMessageBuilders.pubRec().packetId(kernelMsg.packageId()).build();
          case PubEnum.PUB_REL => mqttMsg = MqttMessageBuilders.pubRel().packetId(kernelMsg.packageId()).build();
          case PubEnum.PUB_COMP => mqttMsg = MqttMessageBuilders.pubComp().packetId(kernelMsg.packageId()).build();
          case _ => throw new Exception("no the publish type")
        }
    }
    if (ObjectUtils.isEmpty(mqttMsg)) {
      return false
    }
    if (channel.isWritable) {
      var channelFuture: ChannelFuture = null
      if (brokerConfig.isImmediateBufferFlush) {
        channelFuture = channel.writeAndFlush(mqttMsg)
      } else {
        channelFuture = channel.write(mqttMsg)
      }
      channelFuture.addListener(ChannelFutureListener.CLOSE_ON_FAILURE)
     true
    }else {
      ReferenceCountUtil.safeRelease(mqttMsg.payload)
      false
    }
  }



  /**
   * bound session for the channel
   *
   * @return
   */
  override def session(): ISession = boundSession

}
