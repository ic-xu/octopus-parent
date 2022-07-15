package io.octopus.scala.broker.mqtt.server

import io.netty.buffer.Unpooled
import io.octopus.kernel.kernel.interceptor.PostOfficeNotifyInterceptor
import io.octopus.kernel.kernel.message.{KernelPayloadMessage, PubEnum, MsgQos, MsgRouter, PacketIPackageId}
import io.octopus.kernel.kernel.queue.StoreMsg
import io.octopus.kernel.kernel.repository.IRetainedRepository
import io.octopus.kernel.kernel.security.ReadWriteControl
import io.octopus.kernel.kernel.subscriptions.{ISubscriptionsDirectory, Subscription, Topic}
import io.octopus.kernel.kernel.{IPostOffice, ISession, ISessionResistor}
import io.octopus.kernel.utils.ObjectUtils
import org.slf4j.LoggerFactory

import java.util
import java.util.stream.Collectors

/**
 * 消息分发组件
 *
 * @param subscriptionsDirectory subscriptionsDirectory
 * @param retainedRepository     retainedRepository
 * @param sessionResistor        sessionResistor
 * @param interceptor            interceptor
 * @param authorizator           authorizator
 */
class PostOffice(subscriptionsDirectory: ISubscriptionsDirectory,
                 retainedRepository: IRetainedRepository,
                 sessionResistor: ISessionResistor,
                 interceptor: PostOfficeNotifyInterceptor, authorizator: ReadWriteControl) extends IPostOffice {


  private val LOGGER = LoggerFactory.getLogger(classOf[PostOffice])
  private var adminUser: java.util.List[String] = _


//  /**
//   * public to only clientId
//   *
//   * @param storeMsg storeMsg
//   * @param clientId clientId
//   */
//  def publishMessage2ClientId(storeMsg: StoreMsg[KernelMsg], clientId: String): Unit = {
//    val msg = storeMsg.getMsg
//    publish2ClientId(clientId, msg.getTopic, msg.getQos, storeMsg, directPublish = true)
//    ReferenceCountUtil.release(storeMsg.getMsg)
//  }


  /**
   * Intended usage is only for embedded versions of the broker, where the hosting application
   * want to use the broker to send a publish message. Like normal external publish message but
   * with some changes to avoid security check, and the handshake phases for Qos1 and Qos2. It
   * also doesn't notifyTopicPublished because using internally the owner should already know
   * where it's publishing.
   *
   * @param msg the message to publish
   */
  def internalPublish(msg: KernelPayloadMessage): Unit = {
    val qos = msg.getQos
    val topic = new Topic(msg.getTopic)
    LOGGER.debug("Sending internal PUBLISH message Topic={}, qos={}", topic, qos)
    publish2Subscribers(topic, isNeedBroadcasting = false, new StoreMsg[KernelPayloadMessage](msg, null))
    if (!msg.isRetain) return
    if (qos == MsgQos.AT_MOST_ONCE || msg.getPayload.readableBytes == 0) { // QoS == 0 && retain => clean old retained
      retainedRepository.cleanRetained(topic)
      return
    }
    retainedRepository.retain(topic, msg)
  }


  /**
   * 1）、校验session是否有消息发布权限
   * 2）、落盘处理
   * 3）、分发消息
   * 4）、处理保留消息
   *
   * @param msg         消息体
   * @param fromSession 消息来源
   * @return 结果
   */
  override def processReceiverMsg(msg: KernelPayloadMessage, fromSession: ISession): java.lang.Boolean = {
    // 校验session是否有消息发布权限，
    val topic = new Topic(msg.getTopic)
    if (!authorizator.canWrite(topic, fromSession.getUsername, fromSession.getClientId)) {
      LOGGER.error("MQTT client: {} is not authorized to publish on topic: {}", fromSession.getClientId, topic)
      return false
    }

    //处理刷盘逻辑
    val storeMsg = processFlushDisk(msg)

    // 分发消息
    publish2Subscribers(topic, isNeedBroadcasting = false, storeMsg)

    // 如果消息需要存储，则调用存储组件
    processRetainMsg(msg, topic)

  }


  /**
   * 处理刷盘逻辑
   *
   * @param msg msg
   * @return
   */
  def processFlushDisk(msg: KernelPayloadMessage): StoreMsg[KernelPayloadMessage] = {
    //TODO 统一刷盘处理
    //    msgQueue.offer(msg)
    new StoreMsg[KernelPayloadMessage](msg, null)
  }


  /**
   * publish2Subscribers: publish message to ever one client
   *
   * @param storeMsg           msg
   * @param topic              topic
   * @param isNeedBroadcasting if send message to other broker
   */
  private def publish2Subscribers(topic: Topic, isNeedBroadcasting: Boolean, storeMsg: StoreMsg[KernelPayloadMessage]): Unit = {
    val topicMatchingSubscriptions = subscriptionsDirectory.matchQosSharpening(topic, isNeedBroadcasting)
    topicMatchingSubscriptions.forEach(sub => {
      //处理 qos,按照两个中比较小的一个发送
      val qos = MsgQos.lowerQosToTheSubscriptionDesired(sub, storeMsg.getMsg.getQos)
      // 发送某一个
      publish2ClientId(sub.getClientId, sub.getTopicFilter.getValue, qos, storeMsg, directPublish = false)
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
  private def publish2ClientId(clientId: String, topicName: String, qos: MsgQos, storeMsg: StoreMsg[KernelPayloadMessage], directPublish: Boolean): Unit = {
    val targetSession = this.sessionResistor.retrieve(clientId)
    val isSessionPresent = targetSession != null
    if (isSessionPresent) {
      LOGGER.debug("Sending PUBLISH message to active subscriber CId: {}, topicFilter: {}, qos: {}", clientId, topicName, qos)
      // we need to retain because duplicate only copy r/w indexes and don't retain() causing refCnt = 0
      targetSession.sendMsgAtQos(storeMsg, directPublish)
    }
    else { // If we are, the subscriber disconnected after the subscriptions tree selected that session as a
      // destination.
      LOGGER.debug("PUBLISH to not yet present session. CId: {}, topicFilter: {}, qos: {}", clientId, topicName, qos)
    }
  }


  /**
   * store retainMsg
   *
   * @param msg   msg
   * @param topic topic
   */
  private def processRetainMsg(msg: KernelPayloadMessage, topic: Topic): java.lang.Boolean = {
    if (msg.isRetain) {
      if (!msg.getPayload.isReadable) {
        retainedRepository.cleanRetained(topic)
        true
      } else if (msg.getQos == MsgQos.AT_MOST_ONCE) {
        retainedRepository.cleanRetained(topic)
        true
      } else { // before wasn't stored
        retainedRepository.retain(topic, msg)
      }
    } else {
      true
    }
  }


  def addAdminUser(registerUser: Array[String]): Unit = {
    adminUser = new util.ArrayList[String]
    registerUser.foreach(userName => {
      adminUser.add(userName)
    })
  }

  def fireWill(will: KernelPayloadMessage, session: ISession): Unit = {
    val topic = new Topic(will.getTopic)
    publish2Subscribers(topic, isNeedBroadcasting = true, new StoreMsg[KernelPayloadMessage](will, null))
    //retained message
    if (will.isRetain) if (!will.getPayload.isReadable) retainedRepository.cleanRetained(topic)
    else { // before wasn't stored
      retainedRepository.retain(topic, will)
    }
  }


  // TODO 发布保留的订阅消息
  private def publishRetainedMessagesForSubscriptions(clientID: String, newSubscriptions: util.List[Subscription]): Unit = {
    val targetSession = this.sessionResistor.retrieve(clientID)
    newSubscriptions.forEach(subscription => {
      val topicFilter = subscription.getTopicFilter.toString
      val retainedMsgs = retainedRepository.retainedOnTopic(topicFilter)
      if (retainedMsgs.isEmpty) { // not found
        //continue
        // todo: continue is not supported
      } else {
        retainedMsgs.forEach(retainedMsg => {
          val retainedQos = retainedMsg.qosLevel()
          val qos = MsgQos.lowerQosToTheSubscriptionDesired(subscription, retainedQos)
          val payloadBuf = Unpooled.wrappedBuffer(retainedMsg.getPayload)
          val message = new KernelPayloadMessage(short2Short(0), qos, MsgRouter.TOPIC, retainedMsg.getTopic.getValue, payloadBuf, true,PubEnum.PUBLISH)
          // sendRetainedPublishOnSessionAtQos
          targetSession.sendMsgAtQos(new StoreMsg[KernelPayloadMessage](message, null), false)
          //                targetSession.sendRetainedPublishOnSessionAtQos(retainedMsg.getTopic(), qos, payloadBuf);
        })
      }
    })

  }

  /**
   * 订阅消息
   *
   * @param topicStr topic
   * @param qos      qos
   * @param clientId clientId
   * @return boolean
   */
  override def subscription(fromSession: ISession, topicStr: String, qos: MsgQos, clientId: String): Boolean = {
    val subscription = new Subscription(clientId, new Topic(topicStr), qos)
    val subscriptionsList = new util.ArrayList[Subscription]()
    subscriptionsList.add(subscription)
    subscriptions(fromSession, subscriptionsList).size() > 0
  }


  override def subscriptions(fromSession: ISession, subscriptions: util.List[Subscription]): util.List[Subscription] = {
    authorizator.verifyTopicsReadAccess(fromSession.getClientId, fromSession.getUsername, subscriptions)
    val newSubscriptions: java.util.List[Subscription] = subscriptions.stream.filter(sub => sub.getRequestedQos != MsgQos.FAILURE).collect(Collectors.toList[Subscription])
    //    newSubscriptions.forEach(subscription => subscriptions.add(subscription))

    // add the subscriptions to Session
    //    val session = sessionFactory.retrieve(clientId)
    // 订阅消息
    newSubscriptions.forEach(sub => subscriptionsDirectory.add(sub))

    // 发布保留的订阅消息
    publishRetainedMessagesForSubscriptions(fromSession.getClientId, newSubscriptions)
    newSubscriptions.forEach(subscription => {
      if (!ObjectUtils.isEmpty(interceptor)) {
        interceptor.notifyTopicSubscribed(subscription, fromSession.getUsername)
      }
    })

    newSubscriptions
  }


  override def unSubscriptions(fromSession: ISession, topics: util.Set[String]): Unit = {
    val clientID = fromSession.getClientId
    topics.forEach(t => {
      val topic = new Topic(t)
      val validTopic = topic.isValid
      if (!validTopic) { // close the connection, not valid topicFilter is a protocol violation
        fromSession.handleConnectionLost()
        LOGGER.warn("Topic filter is not valid. CId={}, topics: {}, offending topic filter: {}", clientID, topics, topic)
        return
      }
      LOGGER.trace("Removing subscription. CId={}, topic={}", clientID, topic)
      subscriptionsDirectory.removeSubscription(topic, clientID)
      if(!ObjectUtils.isEmpty(interceptor)){
        interceptor.notifyTopicUnsubscribed(topic.toString, clientID, fromSession.getUsername)
      }
    })
  }

  /**
   * cleanSubscribe
   */
  def cleanSubscribe(session: ISession, topicStrSet: util.Set[String]): Unit = {
    topicStrSet.forEach(topicStr => {
      LOGGER.trace("Removing subscription. CId={}, topic={}", session.getClientId, topicStr)
      subscriptionsDirectory.removeSubscription(new Topic(topicStr), session.getClientId)
    })
  }
}
