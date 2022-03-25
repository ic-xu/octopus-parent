package io.octopus.scala.broker

import io.handler.codec.mqtt._
import io.netty.buffer.Unpooled
import io.netty.util.ReferenceCountUtil
import io.octopus.base.interfaces.IRetainedRepository
import io.octopus.base.queue.StoreMsg
import io.octopus.base.subscriptions.{ISubscriptionsDirectory, Subscription, Topic}
import io.octopus.base.utils.{MqttMessageUtils, NettyUtils}
import io.octopus.broker.Utils
import io.octopus.broker.security.ReadWriteControl
import io.octopus.interception.BrokerNotifyInterceptor
import io.octopus.scala.broker.session.SessionResistor
import org.slf4j.LoggerFactory

import java.util
import java.util.stream.Collectors

class PostOffice(subscriptions: ISubscriptionsDirectory, retainedRepository: IRetainedRepository, sessionFactory: SessionResistor,
                 interceptor: BrokerNotifyInterceptor, authorizator: ReadWriteControl) {


  private val LOGGER = LoggerFactory.getLogger(classOf[PostOffice])
  private var registerUserName: java.util.List[String] = _


  def addRegisterUserName(registerUser: Array[String]): Unit = {
    registerUserName = new util.ArrayList[String]
    registerUser.foreach(userName => {
      registerUserName.add(userName)
    })
  }

  def fireWill(will: MqttWillMessage, connection: MQTTConnection): Unit = {
    val topic = new Topic(will.getTopic)
    val willPubMessage = MqttMessageUtils.notRetainedPublish(will.getTopic, will.getQos, will.getPayload)
    publish2Subscribers(willPubMessage, topic, isNeedBroadcasting = true, null)
    //retained message
    if (will.getRetained) if (!will.getPayload.isReadable) retainedRepository.cleanRetained(topic)
    else { // before wasn't stored
      val fixedHeader = new MqttFixedHeader(MqttMessageType.PUBLISH, false, will.getQos, true, 0)
      val varHeader = new MqttPublishVariableHeader(will.getTopic, connection.boundSession.nextPacketId)
      val mqttPublishMessage = new MqttPublishMessage(fixedHeader, varHeader, will.getPayload)
      retainedRepository.retain(topic, mqttPublishMessage)
    }
  }


  def subscribeClientToTopics(msg: MqttSubscribeMessage, clientId: String, username: String, mqttConnection: MQTTConnection): Unit = { // verify which topics of the subscribe ongoing has read access permission
    val messageID = Utils.messageId(msg)
    val ackTopics = authorizator.verifyTopicsReadAccess(clientId, username, msg)
    //移除系统订阅
    ackTopics.removeIf(subscrib => subscrib.topicName.startsWith("$SYS_") && !registerUserName.contains(username))
    //create mqttSubAckMessageBody
    val ackMessage = doAckMessageFromValidateFilters(ackTopics, messageID)
    // store topics subscriptions in session


    val newSubscriptions: java.util.List[Subscription] = ackTopics.stream.filter(req => req.qualityOfService != MqttQoS.FAILURE).map(req => {
      val topic = new Topic(req.topicName)
      new Subscription(clientId, topic, req.qualityOfService)
    }).collect(Collectors.toList[Subscription])


    newSubscriptions.forEach(subscription => subscriptions.add(subscription))

    // add the subscriptions to Session
    val session = sessionFactory.retrieve(clientId)
    // 把用户的订阅消息保存到自己的session 中。
    session.addSubscriptions(newSubscriptions)
    // send ack message
    mqttConnection.sendSubAckMessage(messageID, ackMessage)
    //发布保留的订阅消息
    publishRetainedMessagesForSubscriptions(clientId, newSubscriptions)
    newSubscriptions.forEach(subscription => interceptor.notifyTopicSubscribed(subscription, username))
  }


  //发布保留的订阅消息
  private def publishRetainedMessagesForSubscriptions(clientID: String, newSubscriptions: util.List[Subscription]): Unit = {
    val targetSession = this.sessionFactory.retrieve(clientID)
    newSubscriptions.forEach(subscription => {
      val topicFilter = subscription.getTopicFilter.toString
      val retainedMsgs = retainedRepository.retainedOnTopic(topicFilter)
      if (retainedMsgs.isEmpty) { // not found
        //continue //todo: continue is not supported

      } else {
        retainedMsgs.forEach(retainedMsg => {
          val retainedQos = retainedMsg.qosLevel
          val qos = lowerQosToTheSubscriptionDesired(subscription, retainedQos)
          val payloadBuf = Unpooled.wrappedBuffer(retainedMsg.getPayload)
          // sendRetainedPublishOnSessionAtQos
          targetSession.sendPublishOnSessionAtQos(new StoreMsg[IMessage](MqttMessageUtils.notRetainedPublish(retainedMsg.getTopic.getValue, qos, payloadBuf), null), false)
          // targetSession.sendRetainedPublishOnSessionAtQos(retainedMsg.getTopic(), qos, payloadBuf);
        })
      }
    })
  }


  /**
   * publish Message to a client by clientId
   *
   * @param clientId  clientId
   * @param topicName topic
   * @param qos       qos
   * @param storeMsg  storeMsg
   */
  private def publish2ClientId(clientId: String, topicName: String, qos: MqttQoS, storeMsg: StoreMsg[IMessage], directPublish: Boolean): Unit = {
    val targetSession = this.sessionFactory.retrieve(clientId)
    val isSessionPresent = targetSession != null
    if (isSessionPresent) {
      LOGGER.debug("Sending PUBLISH message to active subscriber CId: {}, topicFilter: {}, qos: {}", clientId, topicName, qos)
      // we need to retain because duplicate only copy r/w indexes and don't retain() causing refCnt = 0
      targetSession.sendPublishOnSessionAtQos(storeMsg, directPublish)
    }
    else { // If we are, the subscriber disconnected after the subscriptions tree selected that session as a
      // destination.
      LOGGER.debug("PUBLISH to not yet present session. CId: {}, topicFilter: {}, qos: {}", clientId, topicName, qos)
    }

  }

  /**
   * compare qos and get the less one
   *
   * @param sub sub
   * @param qos qos
   * @return qos
   */
  def lowerQosToTheSubscriptionDesired(sub: Subscription, qos: MqttQoS): MqttQoS = {
    var newQos: MqttQoS = qos
    if (qos.value > sub.getRequestedQos.value) newQos = sub.getRequestedQos
    newQos
  }


  /**
   * Create the subAck response from a list of topicFilters
   */
  private def doAckMessageFromValidateFilters(topicFilters: util.List[MqttTopicSubscription], messageId: Int) = {
    val grantedQoSLevels = new util.ArrayList[Integer]
    topicFilters.forEach(req => {
      grantedQoSLevels.add(req.qualityOfService.value)
    })
    val fixedHeader = new MqttFixedHeader(MqttMessageType.SUBACK, false, MqttQoS.AT_MOST_ONCE, false, 0)
    val payload = new MqttSubAckPayload(grantedQoSLevels)
    new MqttSubAckMessage(fixedHeader, MqttMessageIdVariableHeader.from(messageId), payload)
  }

  def cleanSubscribe(subs: util.List[Subscription]): Unit = {
    subs.forEach(subscription => {
      LOGGER.trace("Removing subscription. CId={}, topic={}", subscription.getClientId, subscription.getTopicFilter)
      subscriptions.removeSubscription(subscription.getTopicFilter, subscription.getClientId)
    })
  }


  def unsubscribe(topics: util.List[String], mqttConnection: MQTTConnection, messageId: Int): Unit = {
    val clientID = mqttConnection.getClientId
    topics.forEach(t => {
      val topic = new Topic(t)
      val validTopic = topic.isValid
      if (!validTopic) { // close the connection, not valid topicFilter is a protocol violation
        mqttConnection.dropConnection()
        LOGGER.warn("Topic filter is not valid. CId={}, topics: {}, offending topic filter: {}", clientID, topics, topic)
        return
      }
      LOGGER.trace("Removing subscription. CId={}, topic={}", clientID, topic)
      subscriptions.removeSubscription(topic, clientID)
      val session = sessionFactory.retrieve(clientID)
      session.unSubscriptions(topics)
      val username = NettyUtils.userName(mqttConnection.getChannel)
      interceptor.notifyTopicUnsubscribed(topic.toString, clientID, username)
    })

    // ack the client
    mqttConnection.sendUnSubAckMessage(topics, clientID, messageId)
  }


  /**
   * no write disk
   *
   * @param topic    topic
   * @param username username
   * @param clientId client
   * @param msg      msg
   */
  def receivedPublishQos0(topic: Topic, username: String, clientId: String, msg: MqttPublishMessage): Unit = {
    if (!authorizator.canWrite(topic, username, clientId)) {
      LOGGER.error("MQTT client: {} is not authorized to publish on topic: {}", clientId, topic)
      return
    }
    publish2Subscribers(msg, topic, isNeedBroadcasting = false, new StoreMsg[IMessage](msg, null))
    if (msg.fixedHeader.isRetain) retainedRepository.cleanRetained(topic)
  }


  /**
   * public to only clientId
   *
   * @param storeMsg storeMsg
   * @param clientId clientId
   */
  def publishMessage2ClientId(storeMsg: StoreMsg[IMessage], clientId: String): Unit = {
    val msg = storeMsg.getMsg
    val mqttPublishMessage = msg.asInstanceOf[MqttPublishMessage]
    publish2ClientId(clientId, mqttPublishMessage.variableHeader.topicName, mqttPublishMessage.fixedHeader.qosLevel, storeMsg, directPublish = true)
    ReferenceCountUtil.release(storeMsg.getMsg)
  }


  /**
   * publish message to client
   *
   * @param storeMsg message
   */
  def publishMessage(storeMsg: StoreMsg[IMessage]): Unit = {
    val msg = storeMsg.getMsg
    msg match {
      case message: MqttPublishMessage => publishAtQos(message, storeMsg)
      case _ =>
    }
  }


  /**
   * Intended usage is only for embedded versions of the broker, where the hosting application
   * want to use the broker to send a publish message. Like normal external publish message but
   * with some changes to avoid security check, and the handshake phases for Qos1 and Qos2. It
   * also doesn't notifyTopicPublished because using internally the owner should already know
   * where it's publishing.
   *
   * @param msg the message to publish
   */
  def internalPublish(msg: MqttPublishMessage): Unit = {
    val qos = msg.fixedHeader.qosLevel
    val topic = new Topic(msg.variableHeader.topicName)
    LOGGER.debug("Sending internal PUBLISH message Topic={}, qos={}", topic, qos)
    publish2Subscribers(msg, topic, isNeedBroadcasting = false, null)
    if (!msg.fixedHeader.isRetain) return
    if (qos == MqttQoS.AT_MOST_ONCE || msg.payload.readableBytes == 0) { // QoS == 0 && retain => clean old retained
      retainedRepository.cleanRetained(topic)
      return
    }
    retainedRepository.retain(topic, msg)
  }

  /**
   * publish message to client
   *
   * @param msg message
   */
  def publishAtQos(msg: MqttPublishMessage, storeMsg: StoreMsg[IMessage]): Unit = {
    val topic = new Topic(msg.variableHeader.topicName)
    publish2Subscribers(msg, topic, isNeedBroadcasting = false, storeMsg)
    if (msg.fixedHeader.qosLevel == MqttQoS.AT_MOST_ONCE)
      cleanRetained(msg, topic)
    else storeRetainMsg(msg, topic)
  }


  /**
   * clean retain message
   *
   * @param msg   msg
   * @param topic topic
   */
  private def cleanRetained(msg: MqttPublishMessage, topic: Topic): Unit = {
    if (msg.fixedHeader.isRetain) retainedRepository.cleanRetained(topic)
  }

  /**
   * store retainMsg
   *
   * @param msg   msg
   * @param topic topic
   */
  private def storeRetainMsg(msg: MqttPublishMessage, topic: Topic): Unit = {
    if (msg.fixedHeader.isRetain) if (!msg.payload.isReadable) retainedRepository.cleanRetained(topic)
    else { // before wasn't stored
      retainedRepository.retain(topic, msg)
    }
  }


  /**
   * publish2Subscribers: publish message to ever one client
   *
   * @param msg                msg
   * @param topic              topic
   * @param isNeedBroadcasting if send message to other broker
   */
  private def publish2Subscribers(msg: MqttPublishMessage, topic: Topic, isNeedBroadcasting: Boolean, storeMsg: StoreMsg[IMessage]): Unit = {
    val topicMatchingSubscriptions = subscriptions.matchQosSharpening(topic, isNeedBroadcasting)
    topicMatchingSubscriptions.forEach(sub => {
      val qos = lowerQosToTheSubscriptionDesired(sub, msg.fixedHeader.qosLevel)
      publish2ClientId(sub.getClientId, sub.getTopicFilter.getValue, qos, storeMsg, directPublish = false)
    })
  }


  /**
   * notify MqttConnectMessage after connection established (already pass login).
   *
   * @param msg msg
   */
  def dispatchConnection(msg: MqttConnectMessage): Unit = {
    interceptor.notifyClientConnected(msg)
  }

  def dispatchDisconnection(clientId: String, userName: String): Unit = {
    interceptor.notifyClientDisconnected(clientId, userName)
  }

  def dispatchConnectionLost(clientId: String, userName: String): Unit = {
    interceptor.notifyClientConnectionLost(clientId, userName)
  }
}
