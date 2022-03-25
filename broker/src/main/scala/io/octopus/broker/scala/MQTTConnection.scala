//package io.octopus.broker.scala
//
//import io.handler.codec.mqtt.MqttConnectReturnCode.{CONNECTION_ACCEPTED, CONNECTION_REFUSED_BAD_USER_NAME_OR_PASSWORD, CONNECTION_REFUSED_IDENTIFIER_REJECTED, CONNECTION_REFUSED_SERVER_UNAVAILABLE, CONNECTION_REFUSED_UNACCEPTABLE_PROTOCOL_VERSION}
//import io.handler.codec.mqtt.{MqttConnAckMessage, MqttConnectMessage, MqttConnectPayload, MqttConnectReturnCode, MqttCustomerMessage, MqttMessage, MqttMessageBuilders, MqttMessageType, MqttPublishMessage, MqttSubAckMessage, MqttSubscribeMessage, MqttUnsubscribeMessage, MqttVersion}
//import io.netty.channel.ChannelFutureListener.{CLOSE_ON_FAILURE, FIRE_EXCEPTION_ON_FAILURE}
//import io.netty.channel.{Channel, ChannelFutureListener}
//import io.octopus.broker.SessionRegistry.SessionCreationResult
//import io.octopus.broker.config.BrokerConfiguration
//import io.octopus.broker.exception.SessionCorruptedException
//import io.octopus.broker.handler.CustomerHandler
//import io.octopus.broker.security.IAuthenticator
//import io.octopus.broker.{PostOffice, Session, SessionRegistry}
//import io.octopus.interception.BrokerNotifyInterceptor
//import io.octopus.utils.NettyUtils
//import org.slf4j.{Logger, LoggerFactory}
//
//import java.util.UUID
//
//class MQTTConnection(channel: Channel, brokerConfig: BrokerConfiguration, authenticator: IAuthenticator, sessionRegistry: SessionRegistry, postOffice: PostOffice, interceptor: BrokerNotifyInterceptor) {
//
//  val LOGGER: Logger = LoggerFactory.getLogger(classOf[MQTTConnection])
//
//  var boundSession: Session = _
//
//
//  def handleMessage(msg: MqttMessage): Unit = {
//    val messageType = msg.fixedHeader.messageType
//    LOGGER.debug("Received MQTT message, type: {}, channel: {}", messageType, channel)
//    messageType match {
//      case MqttMessageType.CUSTOMER => processCustomer(msg.asInstanceOf[MqttCustomerMessage])
//      case MqttMessageType.CONNECT => processConnect(msg.asInstanceOf[MqttConnectMessage])
//      case MqttMessageType.SUBSCRIBE => processSubscribe(msg.asInstanceOf[MqttSubscribeMessage])
//      case MqttMessageType.UNSUBSCRIBE => processUnsubscribe(msg.asInstanceOf[MqttUnsubscribeMessage])
//      case MqttMessageType.PUBLISH => processPublish(msg.asInstanceOf[MqttPublishMessage])
//      case MqttMessageType.PUBREC => processPubRec(msg)
//      case MqttMessageType.PUBCOMP => processPubComp(msg)
//      case MqttMessageType.PUBREL => processPubRel(msg)
//      case MqttMessageType.DISCONNECT => processDisconnect(msg)
//      case MqttMessageType.PUBACK => processPubAck(msg)
//      case MqttMessageType.PINGREQ => processPing()
//      case _ =>
//        LOGGER.error("Unknown MessageType: {}, channel: {}", messageType, channel)
//    }
//  }
//
//  private[broker] def processCustomer(msg: MqttCustomerMessage): Unit = {
//    CustomerHandler.processMessage(msg, null, sessionRegistry)
//  }
//
//  private[broker] def processConnect(msg: MqttConnectMessage): Unit = {
//    val payload = msg.payload
//    var clientId = payload.clientIdentifier
//    val username = payload.userName
//    LOGGER.trace("Processing CONNECT message. CId={} username: {} channel: {}", clientId, username, channel)
//    if (isNotProtocolVersion(msg, MqttVersion.MQTT_3_1) && isNotProtocolVersion(msg, MqttVersion.MQTT_3_1_1) && isNotProtocolVersion(msg, MqttVersion.MQTT_2)) {
//      LOGGER.warn("MQTT protocol version is not valid. CId={} channel: {}", clientId, channel)
//      abortConnection(CONNECTION_REFUSED_UNACCEPTABLE_PROTOCOL_VERSION)
//      return
//    }
//    val cleanSession = msg.variableHeader.isCleanSession
//    if (clientId == null || clientId.length == 0) {
//      if (!brokerConfig.isAllowZeroByteClientId) {
//        LOGGER.info("Broker doesn't permit MQTT empty client ID. Username: {}, channel: {}", username, channel)
//        abortConnection(CONNECTION_REFUSED_IDENTIFIER_REJECTED)
//        return
//      }
//      if (!cleanSession) {
//        LOGGER.info("MQTT client ID cannot be empty for persistent session. Username: {}, channel: {}", username, channel)
//        abortConnection(CONNECTION_REFUSED_IDENTIFIER_REJECTED)
//        return
//      }
//      // Generating client id.
//      clientId = UUID.randomUUID.toString.replace("-", "")
//      LOGGER.debug("Client has connected with integration generated id: {}, username: {}, channel: {}", clientId, username, channel)
//    }
//    if (!login(msg, clientId)) {
//      abortConnection(CONNECTION_REFUSED_BAD_USER_NAME_OR_PASSWORD)
//      channel.close.addListener(CLOSE_ON_FAILURE)
//      return
//    }
//    val result:SessionCreationResult = null
//    try {
//      LOGGER.trace("Binding MQTTConnection (channel: {}) to session", channel)
//      result = sessionRegistry.createOrReopenSession(msg, clientId, this.getUsername)
//      result.session.bind(null)
//      boundSession = result.session
//    } catch {
//      case scex: SessionCorruptedException =>
//        LOGGER.warn("MQTT session for client ID {} cannot be created, channel: {}", clientId, channel)
//        abortConnection(CONNECTION_REFUSED_SERVER_UNAVAILABLE)
//        return
//    }
//    val isSessionAlreadyPresent = !cleanSession && result.alreadyStored
//    val clientIdUsed = clientId
//    val ackMessage = MqttMessageBuilders.connAck.returnCode(CONNECTION_ACCEPTED).sessionPresent(isSessionAlreadyPresent).build
//    channel.writeAndFlush(ackMessage).addListener((future: ChannelFuture) => {
//      def foo(future: ChannelFuture) = if (future.isSuccess) {
//        LOGGER.trace("CONNACK sent, channel: {}", channel)
//        if (!result.session.completeConnection) { //change the session status
//          // send DISCONNECT and close the channel
//          val disconnectMsg = MqttMessageBuilders.disconnect.build
//          channel.writeAndFlush(disconnectMsg).addListener(ChannelFutureListener.CLOSE)
//          LOGGER.warn("CONNACK is sent but the session created can't transition in CONNECTED state")
//        }
//        else { //clientIdUsed == clientId
//          NettyUtils.clientID(channel, clientIdUsed)
//          // 连接标记设置为true
//          connected = true
//          // OK continue with sending queued messages and normal flow
//          //notify other offline
//          if (result.mode eq SessionRegistry.CreationModeEnum.REOPEN_EXISTING) result.session.sendQueuedMessagesWhileOffline()
//          initializeKeepAliveTimeout(channel, msg, clientIdUsed)
//          setupInflictResender(channel)
//          postOffice.dispatchConnection(msg)
//          LOGGER.trace("dispatch connection: {}", msg)
//        }
//      }
//      else {
//        boundSession.disconnect()
//        sessionRegistry.remove(boundSession)
//        LOGGER.error("CONNACK send failed, cleanup session and close the connection", future.cause)
//        channel.close
//      }
//
//      foo(future)
//    }.asInstanceOf[ChannelFutureListener])
//  }
//
//
//  private def isNotProtocolVersion(msg: MqttConnectMessage, version: MqttVersion) = msg.variableHeader.version != version.protocolLevel
//
//  private def abortConnection(returnCode: MqttConnectReturnCode): Unit = {
//    val badProto = MqttMessageBuilders.connAck.returnCode(returnCode).sessionPresent(false).build
//    channel.writeAndFlush(badProto).addListener(FIRE_EXCEPTION_ON_FAILURE)
//    channel.close.addListener(CLOSE_ON_FAILURE)
//  }
//
//  private def login(msg: MqttConnectMessage, clientId: String): Boolean = { // handle user authentication
//    if (msg.variableHeader.hasUserName) {
//      var pwd = null
//      if (msg.variableHeader.hasPassword) pwd = msg.payload.passwordInBytes
//      else if (!brokerConfig.isAllowAnonymous) {
//        LOGGER.info("Client didn't supply any password and MQTT anonymous mode is disabled CId={}", clientId)
//        return false
//      }
//      val userNameString = msg.payload.userName
//      if (!authenticator.checkValid(clientId, userNameString, pwd)) {
//        LOGGER.info("Authenticator has rejected the MQTT credentials CId={}, username={}", clientId, userNameString)
//        return false
//      }
//      NettyUtils.userName(channel, userNameString)
//      sessionRegistry.registerUserName(userNameString, clientId)
//    }
//    else if (!brokerConfig.isAllowAnonymous) {
//      LOGGER.info("Client didn't supply any credentials and MQTT anonymous mode is disabled. CId={}", clientId)
//      return false
//    }
//    true
//  }
//
//  def handleConnectionLost(): Unit = {
//    val clientID = NettyUtils.clientID(channel)
//    val userName = NettyUtils.userName(channel)
//    if (clientID == null || clientID.isEmpty) return
//    LOGGER.info("Notifying connection lost event. CId: {}, channel: {}", clientID, channel)
//    if (boundSession.hasWill) postOffice.fireWill(boundSession.getWill, this)
//    if (boundSession.isClean) {
//      LOGGER.debug("Remove session for client CId: {}, channel: {}", clientID, channel)
//      /*
//                   *清除用户注册信息
//                   */ postOffice.cleanSubscribe(boundSession.getSubscriptions)
//      sessionRegistry.removeUserClientIdByUsername(userName, clientID)
//      sessionRegistry.remove(boundSession)
//    }
//    else boundSession.disconnect()
//    connected = false
//    //dispatch connection lost to intercept.
//    postOffice.dispatchConnectionLost(clientID, userName)
//    LOGGER.trace("dispatch disconnection: clientId={}, userName={}", clientID, userName)
//  }
//
//  private[broker] def isConnected = connected
//
//  private[broker] def dropConnection(): Unit = {
//    channel.close.addListener(FIRE_EXCEPTION_ON_FAILURE)
//  }
//
//  private[broker] def sendSubAckMessage(messageID: Int, ackMessage: MqttSubAckMessage): Unit = {
//    val clientId = NettyUtils.clientID(channel)
//    LOGGER.trace("Sending SUBACK response CId={}, messageId: {}", clientId, messageID)
//    channel.writeAndFlush(ackMessage).addListener(FIRE_EXCEPTION_ON_FAILURE)
//  }
//
//  def getBoundSession: Session = boundSession.get
//
//  def getSessionRegistry: SessionRegistry = sessionRegistry
//
//  def getPostOffice: PostOffice = postOffice
//
//
//}
