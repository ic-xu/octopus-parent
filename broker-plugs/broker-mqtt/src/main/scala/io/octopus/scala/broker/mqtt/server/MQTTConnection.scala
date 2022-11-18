package io.octopus.scala.broker.mqtt.server

import io.handler.codec.mqtt.MqttProperties.MqttPropertyType
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
    if (isNotProtocolVersion(msg, MqttVersion.MQTT_3_1)
      && isNotProtocolVersion(msg, MqttVersion.MQTT_3_1_1)
      && isNotProtocolVersion(msg, MqttVersion.MQTT_2)
      && isNotProtocolVersion(msg, MqttVersion.MQTT_5)) {
      logger.warn("MQTT protocol version is not valid. CId={} channel: {}", clientId, channel)
      abortConnection(MqttConnectReturnCode.CONNECTION_REFUSED_UNACCEPTABLE_PROTOCOL_VERSION)
      return
    }


    if (isNotProtocolVersion(msg, MqttVersion.MQTT_5)) {
      handlerConnect3(msg, clientId, username)
    } else {
      handlerConnect5(msg, clientId, username)
    }
  }


  def handlerConnect3(msg: MqttConnectMessage, oldClientId: String, username: String): Unit = {
    var clientId: String = oldClientId
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
      boundSession = result.session
      boundSession.bind(this)
    } catch {
      case sessionException: SessionCorruptedException =>
        logger.error(sessionException.getMessage)
        logger.warn("MQTT session for client ID {} cannot be created, channel: {}", clientId, channel)
        abortConnection(MqttConnectReturnCode.CONNECTION_REFUSED_SERVER_UNAVAILABLE)
        return
    }

    val isSessionAlreadyPresent = !cleanSession && result.alreadyStored
    val clientIdUsed = clientId
    val ackMessage = MqttMessageBuilders.connAck.returnCode(MqttConnectReturnCode.CONNECTION_ACCEPTED).sessionPresent(isSessionAlreadyPresent).build
    channel.writeAndFlush(ackMessage).addListener(new ChannelFutureListener() {
      override def operationComplete(future: ChannelFuture): Unit = {
        if (future.isSuccess) {
          logger.trace("CONN - ACK sent, channel: {}", channel)
          if (!boundSession.completeConnection) { //change the session status
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
            if (result.mode eq CreationModeEnum.REOPEN_EXISTING) {
              result.session.sendQueuedMessagesWhileOffline()
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
   * @see https://github.com/emqx/mqtt5.0-cn/blob/master/mqtt/0301-CONNECT.md
   * @param msg
   * @param clientId
   * @param username
   */
  def handlerConnect5(msg: MqttConnectMessage, clientId: String, username: String): Unit = {

    val properties: MqttProperties = msg.variableHeader().properties()

    /*
    17(0x11)字节，会话过期间隔标识符。
    四个字节整形表示的会话到期间隔，单位是秒，如果这个属性在一个控制包中出现了两次，就会视为协议错误。
    如果会话到期间隔设置为 0 或者没有设置，那么当网络连接关闭的时候 Session 就会结束。
    如果会话到期间隔设为 0xFFFFFFFF(UINT_MAX)，那么这个 Session 就不会过期。
    如果会话到期间隔大于 0，那么 服务器和客户端在关闭网络连接的之后还要保存网络连接。[MQTT-3.1.2-23]
    doc
     */
    val sessionExpiryInterval = properties.getProperty(MqttPropertyType.SESSION_EXPIRY_INTERVAL.value()).value()

    /*
    33(0x21)字节 ，接收最大值标识符
    接收最大值由 2 个字节的整形来表示，如果单个报文中该属性出现了多次或它的值为 0 时，则协议错误。
    客户端通过使用这个值来限制同时并行处理 QoS1 和 QoS2 发布的消息数量。现在还没有机制来限制服务器可能会发布的 QoS0　消息。
    接收最大值只会应用在当前网络连接中，如果没有设置接收最大值，那么它就会设为默认值 65535。值被设为0 或者该属性重复从而引发协议错误。
     */
    val receiveMaximum = properties.getProperty(MqttPropertyType.RECEIVE_MAXIMUM.value()).value()


    /*
    39(0x27)字节 ，最大报文长度标识符。

    接收最大值由 4 个字节的整形来表示客户端愿意接收的最大报文长度，如果不存在最大报文长度属性，作为剩余长度编码和协议头大小的结果，除了协议的限制外，不限制数据包大小。

    如果单个报文中该属性出现了多次，或当其值设为 0 时，则协议错误。

    非正式评注 当需要限制最大报文大小的时候，由应用程序来选择合适的最大报文长度。

    这里的报文大小指的是整个 MQTT 控制包的所有字节数。客户端使用最大报文大小来通知服务器，让它不要处理超过这个大小的数据包。

    服务器 不能 发送超过最大报文长度的包给客户端。[MQTT-3.1.2-24]如果客户端收到了超出限制的报文，那么会视为协议错误。如同4.13 节所描述的那样，服务器会在断开连接的时候返回一个带有 0x95（报文太大）原因码的 DISCONNECT 报文。

    如果 Packet 太大以至于不能正常发送，那么服务器就需要丢弃那些 Packet 并且表现得好像已经完成应用消息发送那样。[MQTT-3.1.2-25]

    在共享订阅的情况下，有可能消息太大不能发送给部分客户端，但是另外一部分客户端可以接收到，服务器可以选择不向任何客户端发送消息并丢弃所有的消息，也可以只向那些可以接收到消息的客户端发送消息。

    非正式评注 如果数据包在未发送的情况下被丢弃，则服务器可以将丢弃的数据包放在“死信队列(dead letter queue)”上或执行其他诊断操作。 此类行为超出了本规范的范围。
     */
    val maximumPacketSize = properties.getProperty(MqttPropertyType.MAXIMUM_PACKET_SIZE.value()).value()


    /*
    34(0x22)字节 ，主题别名最大数量标识符。 主题别名最大数量由 2 个字节的整形来表示，如果单个报文中该属性出现了多次，则协议错误。若未设定主题别名最大数量，则就将其默认设为 0。

    该值用来表示客户端从服务器那里接收到的主题别名的最大数量。客户端使用该值来限制它愿意在此 Connection 上保留的主题别名的数量。
    服务器不能在给客户端发送的 PUBLISH 报文中发送超出主题别名最大数量的主题别名[MQTT-3.1.2-26]。

    当值为 0 时则表示客户端在该连接中不会接收任何主题别名。如果主题别名最大数量不存在或值为 0，则服务器不能发送任何主题别名给客户端[MQTT-3.1.2-27]。
     */
    val topicAliasMaximum = properties.getProperty(MqttPropertyType.TOPIC_ALIAS_MAXIMUM.value()).value()


    /*
    25(0x19)字节 ，请求响应信息标识符。
    这个字节只能表示 0 或者 1，如果它表示的值是 0 或 1 以外的值，或者该属性出现多次，那么就会视为协议错误。若未指定请求响应消息，则将其值设为默认值 0。
    客户端使用该值去请求服务器，服务器会在 CONNACK 包中返回响应信息。当请求响应信息设为 0 的时候，意味着服务器不应该返回响应信息了。如果值为 1，那么服务器会在 CONNACK 包中返回响应信息。
    服务器可以在客户端请求响应信息的时候选择不在 CONNACK 中包含响应信息。
     */
    val requestResponseInformation = properties.getProperty(MqttPropertyType.REQUEST_RESPONSE_INFORMATION.value()).value()


    /*
    23(0x17)字节 ，请求问题信息标识符。

    这个字节只能表示 0 或者 1，如果它表示的值是 0 或 1 以外的值，或者该属性出现多次，那么就会视为协议错误。若未指定请求响应消息，则将其值设为默认值 1。

    客户端使用该值来表示是否用户属性或原因码发送失败。

    如果请求问题信息的值被设为 0，服务器可以在 CONNACK 或 DISCONNECT 报文中返回一个原因字符串或用户属性。但是不能发送原因属性或用户属性在其它任何包中，
    PUBLIHS, CONNACK 或 DISCONNECT 包除外。如果值设为 0，而 客户端却在 PUBLISH,CONNACK,DISCONNECT 包以外收到了原因码或用户属性, 那么就应该用一个带有原因码 0x82（协议错误） 的 DISCONNECT 报文去断开连接。

    如果值设为 1，那么服务器就可以在任何被允许的报文中返回原因码或用户属性。
     */
    val requestProblemInformation = properties.getProperty(MqttPropertyType.REQUEST_PROBLEM_INFORMATION.value()).value()

    /*
    38(0x26)字节 ，用户属性标识符。
    由一个 UTF-8 的字符串对表示。
    用户属性可以在单个报文中出现多次，用于表示多个名称，值对。相同的名称也可以出现多次。
    在 CONNECT 报文中的用户属性在客户端发送到服务器的过程中可以被用来发送和连接相关的属性。这些属性的含义不由本规范定义。
     */
    val userProperty = properties.getProperty(MqttPropertyType.USER_PROPERTY.value()).value()


    /*
    21(0x15)字节 ，验证方法标识符。
    该属性由包含用于扩展验证的扩展方法名的 UTF-8 编码字符串表示，当该属性在同一报文中出现多次时，会被视为协议错误。如果不设置验证方法，默认情况下扩展验证不会被启用。请参阅4.12 节。
    如果客户端在 CONNECT 报文中设置了验证方法，那么客户端在到 CONNACK 报文前就不能再发送除 AUTH 包和 DISCONNECT 报文以外的报文。[MQTT-3.1.2-30]
     */
    val authenticationMethod = properties.getProperty(MqttPropertyType.AUTHENTICATION_METHOD.value()).value()


    /*
    3.1.2.11.10 验证数据 Authentication Data
    22(0x16)字节 ，验证数据标识符 由包含验证数据的二进制数据表示，如果 CONNECT 报文在包含验证数据的情况下却不包含验证方法，或者当该属性在 CONNECT 报文中出现时，则会被视为协议错误。
    该数据的内容由验证方法定义，关于扩展验证的更多信息请参考4.12节。
     */
    val authenticationData = properties.getProperty(MqttPropertyType.AUTHENTICATION_DATA.value()).value()

    /**
     * 有效载荷 Payload
     */

    /*
    3.1.3.1 客户端标识符 Client Identifier
    服务端使用客户端标识符 (ClientId) 识别客户端。连接服务端的每个客户端都有唯一的客户端标识符（ClientId）。
    客户端和服务端都必须使用 ClientId 识别两者之间的 MQTT 会话相关的状态 [MQTT-3.1.3-2]。关于会话状态的更多信息请参阅4.1 节。
    客户端标识符 (ClientId) 必须存在而且必须是CONNECT报文有效载荷的第一个字段 [MQTT-3.1.3-3]。
    客户端标识符必须是1.5.3节定义的UTF-8编码字符串 [MQTT-3.1.3-4]。
    服务端必须允许1到23个字节长的UTF-8编码的客户端标识符，客户端标识符只能包含这些字符：“0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ”（大写字母，小写字母和数字）[MQTT-3.1.3-5]。
    服务端可以允许编码后超过23个字节的客户端标识符 (ClientId)。服务端可以允许包含不是上面列表字符的客户端标识符 (ClientId)。
    服务端可以允许客户端提供一个零字节的客户端标识符 (ClientId) ，如果这样做了，服务端必须将这看作特殊情况并分配唯一的客户端标识符给那个客户端[MQTT-3.1.3-6]。
    然后它必须假设客户端提供了那个唯一的客户端标识符，正常处理这个CONNECT报文，并且必须返回 CONNACK 包中被分配的客户端标识符[MQTT-3.1.3-7]。
    如果服务器拒绝了客户端标识符，那么它可以按照在4.13节 错误处理所描述的那样使用带有 0x85 （客户端标识符无效）原因码的 CONNACK 报文去响应 CONNECT 报文，然后必须关闭网络连接。
    非正式评注 客户端实现可以提供一个方便的方法用于生成随机的客户端标识符。使用此方法的客户端应注意避免创建长期孤立的会话。

     */
    val clientIdentifier = msg.payload().clientIdentifier()

    /*
    3.1.3.2.2 遗嘱延迟间隔 Will Delay Interval
    24(0x18) 字节 ，遗嘱延迟间隔标识符。 由四字节整形来表示以秒为单位的遗嘱延迟间隔，当单个 CONNECT 报文中重复出现了该属性则会被视为协议错误，
    如果遗嘱延迟间隔没有设置，那么会将默认值设为 0，也就意味着遗嘱消息的发布不会有任何延迟。
    服务器只有在遗嘱延迟间隔过期或者会话结束的时候才可以发布客户端的遗嘱消息。如果在遗嘱延迟间隔过期之前在这个会话上建立了一个新的网络连接，
    那么服务器就不应该再发送任何遗嘱消息了。
    非正式评注 遗嘱延迟间隔其中一个用途是在临时网络断开并且客户端在遗嘱消息发布之前成功地重新连接并继续其会话的情况下避免发布遗嘱消息。
    非正式评注 如果网络连接使用了已存在的到服务器的网络连接的客户端标识符，那么已存在的连接中的遗嘱消息就会被发送，
    除非新的连接指明了清除开始（Clean Start）为 0 且遗嘱延迟大于 0。如果遗嘱延迟是 0，那么遗嘱消息就会在已存在的网络连接关闭的时候发送出去，
    如果清除开始设置为 1，那么遗嘱消息会因为会话结束。
     */
    val WillDelayInterval = msg.payload().willProperties().getProperty(MqttPropertyType.WILL_DELAY_INTERVAL.value())
    /*
    1(0x01) 字节 ，有效载荷格式指示器标识符。
    有效载荷格式指示器的值分以下两种：
    当该属性值为 0（0x00） 的时候，意味着遗嘱消息是未确定的字节，相当于不发送有效载荷格式指示器。
    当该属性值为 1（0x01） 的时候，意味着遗嘱消息是 UTF-8 的字符数据，在有效载荷中的 UTF-8 数据必须是由 Unicode 规范定义且在 [RFC3629] 中定义的格式良好的 UTF-8 数据。
    服务器必须发送所有未选择有效载荷格式指示器给所有接收应用消息的订阅者[MQTT--3.3.2-4]。接收者也可以验证格式指定的有效载荷，
    以及是否它没有如同4.13 节所描述的那样发送带有 0x99 （有效载荷格式无效）原因码的 PUBACK，PUBREC 或 DISCONNECT报文。
     */
    val payloadFormatIndicator = msg.payload().willProperties().getProperty(MqttPropertyType.PAYLOAD_FORMAT_INDICATOR.value())
    /*
    本属性由 4 字节整形来表示。如果 CONNECT 报文重复出现该属性则会被视为协议错误。

    如果存在该属性，那么这个 4 字节的值就用来表示单位为秒的遗嘱消息的生命周期，并且当服务器发布遗嘱消息的时候会被作为发布过期间隔发送。
     */
    val messageExpiryInterval = msg.payload().willProperties().getProperty(MqttPropertyType.PUBLICATION_EXPIRY_INTERVAL.value())


    /*
    3 (0x03)字节 , 内容类型标识符。 该属性是以 UTF-8 编码，用来描述遗嘱消息内容的字符串，当该属性在同一报文中出现多次时，则会被视为协议错误。该属性的内容由收发消息的应用程序来定义。
     */
    val contentType = msg.payload().willProperties().getProperty(MqttPropertyType.CONTENT_TYPE.value())

    /*
    3.1.3.2.6 响应主题 Response Topic
    8 (0x08) 字节 ，响应主题标识符。 由 UTF-8 编码的字符串，通常是用来作为响应消息中的主题名。当该属性在同一报文中重复出现时，则会被视为协议错误。若响应主题存在，会将遗嘱消息视为一个请求。
     */
    val responseTopic = msg.payload().willProperties().getProperty(MqttPropertyType.RESPONSE_TOPIC.value())

    /*
    .1.3.2.7 关联数据 Correlation Data
    9 (0x09)字节 ，关联数据标识符。

    由二进制数据表示， 该数据通常是给请求消息的发送者中用来鉴别哪一个才是它收到的响应消息的请求。
    若该属性在同一报文中重复出现，则会被视为协议错误。如果没有设定关联数据 ，那么请求者就不需要任何关联数据。
     */
    val correlationData = msg.payload().willProperties().getProperty(MqttPropertyType.CORRELATION_DATA.value())


    abortConnection(MqttConnectReturnCode.CONNECTION_REFUSED_UNSUPPORTED_PROTOCOL_VERSION)
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
    val properties = msg.variableHeader().properties()
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
    } else {
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
