//package io.octopus.scala.mqtt.server.session
//
// package io.octopus.scala.mqtt.broker.session
//
//import io.netty.buffer.ByteBufHolder
//import io.netty.util.ReferenceCountUtil
//import io.octopus.utils.ObjectUtils
//import io.octopus.kernel.connect.AbstractConnection
//import io.octopus.kernel.message.{InFlightPacket, KernelMsg}
//import io.octopus.kernel.queue.{MsgIndex, MsgQueue, SearchData, StoreMsg}
//import io.octopus.kernel.session.{ISession, SessionStatus}
//import io.octopus.kernel.subscriptions.Subscription
//import io.octopus.scala.mqtt.broker.PostOffice
//import org.slf4j.LoggerFactory
//
//import java.net.InetSocketAddress
//import java.util
//import java.util.Optional
//import java.util.concurrent.atomic.{AtomicInteger, AtomicReference}
//import java.util.concurrent.{ConcurrentHashMap, DelayQueue, ExecutorService}
//
//class Session(postOffice: PostOffice, clientId: String, username: String, var clean: Boolean, var willMsg: KernelMsg,
//              messageIndexQueue: java.util.Queue[MsgIndex],
//              inflictWindowSize: Int, clientVersion: Int, msgQueue: MsgQueue[KernelMsg], drainQueueService: ExecutorService) extends ISession {
//  private val logger = LoggerFactory.getLogger(classOf[Nothing])
//  private val FLIGHT_BEFORE_RESEND_MS = 5_000
//  private val status: AtomicReference[SessionStatus] = new AtomicReference[SessionStatus](SessionStatus.DISCONNECTED)
//  //subscriptions save session`s subscriptions，
//  private val subTopicStr: util.Set[String] = new util.HashSet[String]()
//  private val inflictWindow: util.Map[Short, KernelMsg] = new ConcurrentHashMap[Short, KernelMsg]
//  private val inflictTimeouts: util.concurrent.DelayQueue[InFlightPacket] = new DelayQueue[InFlightPacket]
//  private val inflictSlots = new AtomicInteger(inflictWindowSize) // this should be configurable
//  private var udpInetSocketAddress: InetSocketAddress = _
//  private var connection: AbstractConnection = _
//  private val lastPacketId = new AtomicInteger(1)
//
//  def addInflictWindow(inflictWindows: util.Map[Short, KernelMsg]): Unit = {
//    inflictWindows.forEach((packetId, _) => {
//      inflictTimeouts.add(new InFlightPacket(packetId, FLIGHT_BEFORE_RESEND_MS))
//      inflictSlots.decrementAndGet
//    })
//    this.inflictWindow.putAll(inflictWindows)
//  }
//
//
//  def getInflictWindow: util.Map[Short, KernelMsg] = inflictWindow
//
//
//  override def cleanSubscribe(): Unit = {
//    postOffice.cleanSubscribe(this, subTopicStr)
//    unSubscriptions(getSubTopicList)
//  }
//
//  /**
//   * unSubscriptions
//   *
//   * @param topics topic{@link Topic}
//   */
//  def unSubscriptions(topics: util.List[String]): Unit = {
//    // unSubscriptions
//    postOffice.unSubscriptions(this, topics)
//    // remove session`s topic
//    subTopicStr.removeAll(topics)
//  }
//
//
//  /**
//   * 发送消息，postOffice 调用这个消息发送相关消息
//   *
//   * @param storeMsg      msg
//   * @param directPublish Send directly
//   */
//  override def sendPublishOnSessionAtQos(storeMsg: StoreMsg[KernelMsg], directPublish: java.lang.Boolean): Unit = {
//    storeMsg.getMsg.setShortId(nextPacketId)
//    storeMsg.getMsg.getQos match {
//      case MessageQos.AT_MOST_ONCE =>
//        sendPublishQos0(storeMsg, directPublish)
//      //这里发布的时候两个使用同样的处理方法即可
//      case MessageQos.AT_LEAST_ONCE =>
//        sendPublishQos1(storeMsg, directPublish)
//      case MessageQos.EXACTLY_ONCE =>
//        sendPublishQos2(storeMsg, directPublish)
//      case MessageQos.UDP =>
//        logger.error("Not admissible")
//      case _ =>
//        logger.error("Not admissible")
//    }
//  }
//
//
//  def sendPublishQos0(storeMsg: StoreMsg[KernelMsg], directPublish: Boolean): Unit = {
//    connection.sendIfWritableElseDrop(storeMsg.getMsg)
//  }
//
//
//  private def sendPublishQos1(storeMsg: StoreMsg[KernelMsg], directPublish: Boolean): Unit = {
//    if (!connected && isClean) { //pushing messages to disconnected not clean session
//      return
//    }
//    val msg = storeMsg.getMsg
//
//    if (canSkipQueue || directPublish) {
//      //          publishMsg.variableHeader.setPacketId(mqttConnection.nextPacketId)
//      inflictSlots.decrementAndGet
//      val old = inflictWindow.put(msg.getShortId, msg.retain())
//      // If there already was something, release it.
//      if (old.isInstanceOf[ByteBufHolder]) {
//        try ReferenceCountUtil.safeRelease(old)
//        catch {
//          case ignored: Exception =>
//        }
//        inflictSlots.incrementAndGet
//      }
//
//      inflictTimeouts.add(new InFlightPacket(msg.getShortId, FLIGHT_BEFORE_RESEND_MS))
//      connection.sendIfWritableElseDrop(msg)
//      logger.debug("Write direct to the peer, inflict slots: {}", inflictSlots.get)
//      if (inflictSlots.get == 0) connection.flush()
//      // TODO drainQueueToConnection();?
//      //            drainQueueToConnection();
//    } else {
//      offerMsgIndex(storeMsg.getIndex, msg)
//      drainQueueToConnection()
//    }
//  }
//
//  private def sendPublishQos2(storeMsg: StoreMsg[KernelMsg], directPublish: Boolean): Unit = {
//    if (!connected && isClean) { //pushing messages to disconnected not clean session
//      return
//    }
//    val msg = storeMsg.getMsg
//
//    if (canSkipQueue || directPublish) {
//      //          publishMsg.variableHeader.setPacketId(mqttConnection.nextPacketId)
//      inflictSlots.decrementAndGet
//      val old = inflictWindow.put(msg.getShortId, msg.retain())
//      // If there already was something, release it.
//      if (old.isInstanceOf[ByteBufHolder]) {
//        try ReferenceCountUtil.safeRelease(old)
//        catch {
//          case ignored: Exception =>
//        }
//        inflictSlots.incrementAndGet
//      }
//      inflictTimeouts.add(new InFlightPacket(msg.getShortId, FLIGHT_BEFORE_RESEND_MS))
//      connection.sendIfWritableElseDrop(msg)
//      logger.debug("Write direct to the peer, inflict slots: {}", inflictSlots.get)
//      if (inflictSlots.get == 0) connection.flush()
//      // TODO drainQueueToConnection();?
//      //            drainQueueToConnection();
//    } else {
//      offerMsgIndex(storeMsg.getIndex, msg)
//      drainQueueToConnection()
//    }
//  }
//
//  // persistence msgIndex
//  private def offerMsgIndex(msgIndex: MsgIndex, msg: KernelMsg): Unit = {
//    if (!ObjectUtils.isEmpty(msgIndex)) messageIndexQueue.offer(msgIndex)
//    //    ReferenceCountUtil.safeRelease(publishMsg)
//  }
//
//
//  def pubAckReceived(ackPacketId: Short): Unit = { // TODO remain to invoke in somehow m_interceptor.notifyMessageAcknowledged
//    logger.trace("received a pubAck packetId is {} ", ackPacketId)
//    val removeMsg = inflictWindow.remove(ackPacketId)
//    inflictTimeouts.remove(new InFlightPacket(ackPacketId, FLIGHT_BEFORE_RESEND_MS))
//    if (removeMsg == null) logger.trace("Received a pubAck with not matching packetId  {} ", ackPacketId)
//    else removeMsg match {
//      case holder: ByteBufHolder =>
//        holder.release
//        inflictSlots.incrementAndGet
//      case _ =>
//    }
//    drainQueueToConnection()
//  }
//
////TODO
//  def PubRecReceived(ackPacketId: Short): Boolean = {
//    logger.trace("received a pubAck packetId is {} ", ackPacketId)
//    val exitMsg = inflictWindow.get(ackPacketId)
//    if(null!=exitMsg){
//      inflictTimeouts.remove(new InFlightPacket(ackPacketId, FLIGHT_BEFORE_RESEND_MS))
//      inflictTimeouts.add(new InFlightPacket(ackPacketId, FLIGHT_BEFORE_RESEND_MS))
//
//      exitMsg
//      true
//    }else{
//      logger.trace("Received a pubAck with not matching packetId  {} ", ackPacketId)
//      false
//    }
//  }
//
//  def flushAllQueuedMessages(): Unit = {
//    drainQueueToConnection()
//  }
//
//  def writeAbilityChanged(): Unit = {
//    drainQueueToConnection()
//  }
//
//  def sendQueuedMessagesWhileOffline(): Unit = {
//    logger.trace("Republishing all saved messages for session {} on CId={}", this, this.clientId)
//    drainQueueToConnection()
//  }
//
//  def remoteAddress: Optional[InetSocketAddress] = {
//    if (connected) return Optional.of(connection.remoteAddress)
//    Optional.empty
//  }
//
//
//  def cleanSessionQueue(): Unit = {
//    while (messageIndexQueue.size > 0) {
//      messageIndexQueue.poll()
//    }
//  }
//
//
//  def update(clean: Boolean, will: KernelMsg): Unit = {
//    this.clean = clean
//    this.willMsg = will
//  }
//
//
//  def markConnecting(): Unit = {
//    assignState(SessionStatus.DISCONNECTED, SessionStatus.CONNECTING)
//  }
//
//  def completeConnection: Boolean = assignState(SessionStatus.CONNECTING, SessionStatus.CONNECTED)
//
//  def bind(connection: AbstractConnection): Unit = {
//    this.connection = connection
//  }
//
//  def bindUdpInetSocketAddress(inetSocketAddress: InetSocketAddress): Unit = {
//    this.udpInetSocketAddress = inetSocketAddress
//  }
//
//  def getUdpInetSocketAddress: InetSocketAddress = this.udpInetSocketAddress
//
//  def disconnected: Boolean = status.get eq SessionStatus.DISCONNECTED
//
//
//  def getClientId: String = clientId
//
//  def getSubTopicList = new util.ArrayList[String](subTopicStr)
//
//  /**
//   * uddate the status of session
//   *
//   * @param expected expected Status
//   * @param newState new Status
//   * @return
//   */
//  def assignState(expected: SessionStatus, newState: SessionStatus): Boolean = status.compareAndSet(expected, newState)
//
//  def getWill: KernelMsg = willMsg
//
//  def closeImmediately(): Unit = {
//    connection.dropConnection()
//  }
//
//  private def disconnect(): Unit = {
//    val res = assignState(SessionStatus.CONNECTED, SessionStatus.DISCONNECTING)
//    if (!res) {
//      logger.info("this status is SessionStatus.DISCONNECTING")
//      return
//    }
//    connection = null
//    willMsg = null
//    assignState(SessionStatus.DISCONNECTING, SessionStatus.DISCONNECTED)
//  }
//
//  def isClean: Boolean = clean
//
//
//  // consume the queue
//  private def drainQueueToConnection(): Unit = {
//    drainQueueService.submit(new DrainQueueWorker)
//  }
//
//  /**
//   *
//   * doDrainQueueToConnection
//   */
//  private def doDrainQueueToConnection(): Unit = {
//    reSendInflictNotAcked()
//    while (!messageIndexQueue.isEmpty && inflictHasSlotsAndConnectionIsUp) {
//      val msgIndex = messageIndexQueue.poll()
//      if (!ObjectUtils.isEmpty(msgIndex)) {
//
//        //notify: there must not use foreach(), there queue not implement
//        //  @Override
//        //    public Iterator<MsgIndex> iterator() {
//        //        return null;
//        //    }
//        //and inflictHasSlotsAndConnectionIsUp is true
//        val msg = msgQueue.poll(new SearchData(clientId, msgIndex))
//        //        if (!ObjectUtils.isEmpty(msg)) {
//        //          msg.getMsg match {
//        //            case mqttMessage: MqttMessage =>
//        //              mqttMessage.fixedHeader.messageType match {
//        //                case MqttMessageType.CUSTOMER =>
//        //                case MqttMessageType.PUBLISH =>
//        //                  val msgPub = mqttMessage.asInstanceOf[MqttPublishMessage]
//        //                  msgPub.variableHeader.setPacketId(nextPacketId)
//        //                  if (msgPub.fixedHeader.qosLevel ne MqttQoS.AT_MOST_ONCE) {
//        //                    inflictSlots.decrementAndGet
//        //                    val old = inflictWindow.put(msgPub.variableHeader.packetId, msgPub.copy)
//        //                    ReferenceCountUtil.safeRelease(old)
//        //                    inflictSlots.incrementAndGet
//        //                    inflictTimeouts.add(new InFlightPacket(msgPub.variableHeader.packetId, FLIGHT_BEFORE_RESEND_MS))
//        //                  }
//        //                  connection.sendPublish(msgPub)
//        //
//        //                case MqttMessageType.PUBACK =>
//        //                case MqttMessageType.PUBREC =>
//        //                case MqttMessageType.PUBREL =>
//        //                case MqttMessageType.PUBCOMP =>
//        //                  val variableHeader = mqttMessage.variableHeader.asInstanceOf[MqttMessageIdVariableHeader]
//        //                  inflictSlots.decrementAndGet
//        //                  val packetId = variableHeader.messageId
//        //                  val old = inflictWindow.put(packetId, mqttMessage)
//        //                  ReferenceCountUtil.safeRelease(old)
//        //                  inflictSlots.incrementAndGet
//        //                  inflictTimeouts.add(new InFlightPacket(packetId, FLIGHT_BEFORE_RESEND_MS))
//        //                  val pubRel = connection.pubRel(packetId)
//        //                  connection.sendIfWritableElseDrop(pubRel)
//        //
//        //                case _ =>
//        //
//        //              }
//        //
//        //            case _ => logger.trace("error msg {}", msg)
//        //          }
//        //          ReferenceCountUtil.safeRelease(msg.getMsg)
//        //        }
//      }
//    }
//  }
//
//  def reSendInflictNotAcked(): Unit = {
//    if (this.inflictTimeouts.size == 0) inflictWindow.clear()
//    else {
//      val expired = new util.ArrayList[InFlightPacket](inflictWindowSize)
//      this.inflictTimeouts.drainTo(expired)
//      debugLogPacketIds(expired)
//      expired.forEach(notAckPacketId => {
//        if (inflictWindow.containsKey(notAckPacketId.getPacketId)) {
//          val message = inflictWindow.remove(notAckPacketId.getPacketId)
//          if (null == message) { // Already acked...
//            logger.warn("Already acked...")
//          } else message match {
//            //            case pubRelMsg: MqttPubRelMessage => connection.sendIfWritableElseDrop(pubRelMsg)
//            //            case publishMsg: MqttPublishMessage =>
//            //              //                        MqttPublishMessage publishMsg = publishNotRetainedDuplicated(notAckPacketId, msg.getTopic(), msg.getPublishingQos(), msg.getPayload());
//            //              inflictTimeouts.add(new InFlightPacket(notAckPacketId.getPacketId, FLIGHT_BEFORE_RESEND_MS))
//            //              connection.sendPublish(publishMsg)
//            case _ => logger.warn("Already acked...")
//          }
//          ReferenceCountUtil.safeRelease(message)
//        }
//      })
//    }
//  }
//
//
//  private def inflictHasSlotsAndConnectionIsUp = inflictSlots.get > 0 && connected && connection.getChannel.isWritable
//
//
//  def connected: Boolean = status.get == SessionStatus.CONNECTED
//
//  private def canSkipQueue = messageIndexQueue.isEmpty && inflictSlots.get > 0 && connected && connection.getChannel.isWritable
//
//
//  private def debugLogPacketIds(expired: util.Collection[InFlightPacket]): Unit = {
//    if (!logger.isDebugEnabled || expired.isEmpty) return
//    val sb = new StringBuilder
//    expired.forEach(packet => {
//      sb.append(packet.getPacketId).append(", ")
//    })
//    logger.debug("Resending {} in flight packets [{}]", expired.size, sb)
//  }
//
//
//  override def toString: String = "Session {" + "clientId='" + clientId + '\'' + ", clean=" + clean + ", status=" + status + ", inflightSlots=" + inflictSlots + '}'
//
//
//  class DrainQueueWorker extends Runnable {
//    override def run(): Unit = {
//      try
//        doDrainQueueToConnection()
//      catch {
//        case _: Exception =>
//      }
//    }
//  }
//
//  /**
//   * 接收到一个消息
//   *
//   * @param msg msg；
//   */
//  override def receiveMessage(msg: KernelMsg): Boolean = {
//    postOffice.processReceiverMsg(msg, this)
//  }
//
//
//  /**
//   * sub
//   *
//   * @param subscriptions sbu
//   */
//  override def subscriptions(subscriptions: util.List[Subscription]): util.List[Subscription] = {
//    val subscriptionsSuccess = postOffice.subscriptions(this, subscriptions)
//    // 把用户的订阅消息保存到自己的session 中。
//    subscriptions.addAll(subscriptionsSuccess)
//    subscriptionsSuccess
//  }
//
//
//  override def handleConnectionLost(): Unit = {
//    //TODO connectionLost
//    //    postOffice.dispatchDisconnection(clientID, userName)
//
//    //TODO postOffice.dispatchConnectionLost(clientID, userName)
//
//    //fireWillMsg
//    if (null != getWill) {
////      postOffice.fireWill(getWill:KernelMsg, this)
//    }
//    //    //unSubscription
//    //    cleanSubscribe()
//    //update status
//    disconnect()
//  }
//
//  override def getUsername: String = username
//
//  def nextPacketId: Short = {
//    if (lastPacketId.incrementAndGet > 65535) {
//      lastPacketId.set(1)
//      return 1
//    }
//    lastPacketId.get.toShort
//  }
//}
