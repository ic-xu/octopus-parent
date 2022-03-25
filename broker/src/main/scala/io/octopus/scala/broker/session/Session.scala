package io.octopus.scala.broker.session

import io.handler.codec.mqtt._
import io.netty.buffer.ByteBufHolder
import io.netty.util.ReferenceCountUtil
import io.octopus.base.interfaces.ISession
import io.octopus.base.message.Message
import io.octopus.base.queue.{MsgIndex, MsgQueue, SearchData, StoreMsg}
import io.octopus.base.subscriptions.{Subscription, Topic}
import io.octopus.base.utils.ObjectUtils
import io.octopus.broker.session.SessionStatus
import io.octopus.scala.broker.IConnection
import io.store.message.InFlightPacket
import org.slf4j.LoggerFactory

import java.io.IOException
import java.net.InetSocketAddress
import java.util
import java.util.Optional
import java.util.concurrent.atomic.{AtomicInteger, AtomicReference}
import java.util.concurrent.{ConcurrentHashMap, DelayQueue, ExecutorService}

class Session(clientId: String, var clean: Boolean, var willMsg: MqttWillMessage, messageIndexQueue: java.util.Queue[MsgIndex],
              inflictWindowSize: Int, clientVersion: Int, msgQueue: MsgQueue[IMessage], drainQueueService: ExecutorService) extends ISession {

  private val logger = LoggerFactory.getLogger(classOf[Nothing])
  private val FLIGHT_BEFORE_RESEND_MS = 5_000
  private val status: AtomicReference[SessionStatus] = new AtomicReference[SessionStatus](SessionStatus.DISCONNECTED)
  //subscriptions save session`s subscriptions，
  private val subscriptions: util.Set[Subscription] = new util.HashSet[Subscription]()
  private val inflictWindow: util.Map[Int, IMessage] = new ConcurrentHashMap[Int, IMessage]
  private val inflictTimeouts: util.concurrent.DelayQueue[InFlightPacket] = new DelayQueue[InFlightPacket]
  private val qos2Receiving: util.Map[Integer, MqttPublishMessage] = new ConcurrentHashMap[Integer, MqttPublishMessage]
  private val inflictSlots = new AtomicInteger(inflictWindowSize) // this should be configurable
  private var udpInetSocketAddress: InetSocketAddress = _
  private var connection: IConnection = _
  private val lastPacketId = new AtomicInteger(1)

  def addInflictWindow(inflictWindows: util.Map[Int, IMessage]): Unit = {
    inflictWindows.forEach((packetId, _) => {
      inflictTimeouts.add(new InFlightPacket(packetId, FLIGHT_BEFORE_RESEND_MS))
      inflictSlots.decrementAndGet
    })
    this.inflictWindow.putAll(inflictWindows)
  }


  def getInflictWindow: util.Map[Int, IMessage] = inflictWindow


  def addSubscriptions(newSubscriptions: util.List[Subscription]): Unit = {
    subscriptions.addAll(newSubscriptions)
  }

  /**
   * unSubscriptions
   *
   * @param topic topic{@link Topic}
   */
  def unSubscriptions(topic: util.List[String]): Unit = {
    val remove = new util.ArrayList[Subscription]
    subscriptions.forEach(subscription => {
      if (topic.contains(subscription.getTopicFilter.getValue)) remove.add(subscription)
    })

    remove.forEach(subscription => subscriptions.remove(subscription))
  }


  /**
   * @param msg msg
   */
  def processPubRec(msg: MqttMessage): Unit = {
    val packetId: Int = msg.variableHeader.asInstanceOf[MqttMessageIdVariableHeader].messageId.toShort
    val removeMsg = inflictWindow.remove(packetId)
    inflictTimeouts.remove(new InFlightPacket(packetId, FLIGHT_BEFORE_RESEND_MS))
    if (removeMsg == null) {
      logger.warn("Received a pubRec with not matching packetId")
      drainQueueToConnection()
      return
    }
    ReferenceCountUtil.safeRelease(removeMsg)
    inflictWindow.put(packetId, msg)
    inflictTimeouts.add(new InFlightPacket(packetId, FLIGHT_BEFORE_RESEND_MS))
    val pubRel = MqttMessageFactory.newPubRelMessage(packetId)
    connection.sendIfWritableElseDrop(pubRel)
    //        drainQueueToConnection();
  }

  def processPubComp(messageId: Int): Unit = {
    val removeMsg = inflictWindow.remove(messageId)
    inflictTimeouts.remove(new InFlightPacket(messageId, FLIGHT_BEFORE_RESEND_MS))
    if (removeMsg == null) {
      logger.warn("Received a PUBCOMP with not matching packetId")
      return
    }
    try ReferenceCountUtil.release(removeMsg)
    catch {
      case _: Exception =>

    }
    inflictSlots.incrementAndGet
    drainQueueToConnection()
  }


  /**
   * @param storeMsg      msg
   * @param directPublish Send directly
   */
  def sendPublishOnSessionAtQos(storeMsg: StoreMsg[IMessage], directPublish: Boolean): Unit = {

    storeMsg.getMsg match {
      case publishMsg: MqttPublishMessage =>
        publishMsg.variableHeader.setPacketId(nextPacketId)
        publishMsg.fixedHeader.qosLevel match {
          case MqttQoS.AT_MOST_ONCE =>
            sendPublishNotRetainedQos0(storeMsg, directPublish)

          //这里发布的时候两个使用同样的处理方法即可
          case MqttQoS.AT_LEAST_ONCE =>
            sendPublishQos1(storeMsg, directPublish)

          case MqttQoS.EXACTLY_ONCE =>
            sendPublishQos2(storeMsg, directPublish)

          case MqttQoS.FAILURE =>
            logger.error("Not admissible")
          case _ =>
            logger.error("Not admissible")
        }
      case _ =>
    }
  }


  //if version is 2 ,can send customer message
  def sendCustomerMessage(mqttMessage: MqttMessage): Unit = {
    if (clientVersion == MqttVersion.MQTT_2.protocolLevel) connection.sendIfWritableElseDrop(mqttMessage)
  }


  def sendPublishNotRetainedQos0(storeMsg: StoreMsg[IMessage], directPublish: Boolean): Unit = {
    storeMsg.getMsg match {
      case msg: MqttPublishMessage => connection.sendPublish(msg.retain())
      case _ =>
    }
  }


  private def sendPublishQos1(storeMsg: StoreMsg[IMessage], directPublish: Boolean): Unit = {
    if (!connected && isClean) { //pushing messages to disconnected not clean session
      return
    }
    storeMsg.getMsg match {
      case publishMsg: MqttPublishMessage =>
        if (canSkipQueue || directPublish) {
          //          publishMsg.variableHeader.setPacketId(mqttConnection.nextPacketId)
          inflictSlots.decrementAndGet
          val old = inflictWindow.put(publishMsg.variableHeader.packetId, publishMsg.copy)
          // If there already was something, release it.
          if (old.isInstanceOf[ByteBufHolder]) {
            try ReferenceCountUtil.safeRelease(old)
            catch {
              case ignored: Exception =>
            }
            inflictSlots.incrementAndGet
          }
          inflictTimeouts.add(new InFlightPacket(publishMsg.variableHeader.packetId, FLIGHT_BEFORE_RESEND_MS))
          connection.sendPublish(publishMsg.copy())
          logger.debug("Write direct to the peer, inflict slots: {}", inflictSlots.get)
          if (inflictSlots.get == 0) connection.flush()
          // TODO drainQueueToConnection();?
          //            drainQueueToConnection();
        }
        else {
          offerMsgIndex(storeMsg.getIndex, publishMsg)
          drainQueueToConnection()
        }
      case _ =>
    }
  }

  // persistence msgIndex
  private def offerMsgIndex(msgIndex: MsgIndex, publishMsg: MqttPublishMessage): Unit = {
    if (!ObjectUtils.isEmpty(msgIndex)) messageIndexQueue.offer(msgIndex)
    //    ReferenceCountUtil.safeRelease(publishMsg)
  }

  private def sendPublishQos2(storeMsg: StoreMsg[IMessage], directPublish: Boolean): Unit = {
    storeMsg.getMsg match {
      case publishMsg: MqttPublishMessage =>
        if (canSkipQueue || directPublish) {
          //          publishMsg.variableHeader.setPacketId(mqttConnection.nextPacketId)
          inflictSlots.decrementAndGet
          inflictWindow.put(publishMsg.variableHeader.packetId, publishMsg.copy())
          inflictTimeouts.add(new InFlightPacket(publishMsg.variableHeader.packetId, FLIGHT_BEFORE_RESEND_MS))
          connection.sendPublish(publishMsg.copy)
          //            //TODO  drainQueueToConnection(); ?
        }
        else {
          offerMsgIndex(storeMsg.getIndex, publishMsg)
        }
      case _ =>
    }

    drainQueueToConnection()
  }


  def pubAckReceived(ackPacketId: Int): Unit = { // TODO remain to invoke in somehow m_interceptor.notifyMessageAcknowledged
    logger.trace("received a pubAck packetId is {} ", ackPacketId)
    val removeMsg = inflictWindow.remove(ackPacketId)
    inflictTimeouts.remove(new InFlightPacket(ackPacketId, FLIGHT_BEFORE_RESEND_MS))
    if (removeMsg == null) logger.trace("Received a pubAck with not matching packetId  {} ", ackPacketId)
    else removeMsg match {
      case holder: ByteBufHolder =>
        holder.release
        inflictSlots.incrementAndGet
      case _ =>
    }
    drainQueueToConnection()
  }

  def flushAllQueuedMessages(): Unit = {
    drainQueueToConnection()
  }

  def writeAbilityChanged(): Unit = {
    drainQueueToConnection()
  }

  def sendQueuedMessagesWhileOffline(): Unit = {
    logger.trace("Republishing all saved messages for session {} on CId={}", this, this.clientId)
    drainQueueToConnection()
  }


  def sendPublishReceivedQos2(msg: MqttPublishMessage): Unit = {
    val messageId = msg.variableHeader.packetId
    //        msg.retain(); // retain to put in the inflight maptree
    val old = qos2Receiving.put(messageId, msg.copy)
    // In case of evil client with duplicate msgid.
    if (null != old) ReferenceCountUtil.safeRelease(old)
    connection.sendPublishReceived(messageId)
  }

  @throws[IOException]
  def receivedPubRelQos2(messageId: Int): Unit = {
    val removedMsg = qos2Receiving.remove(messageId)
    if (null != removedMsg) {
      connection.receiverQos2(removedMsg, clientId, connection.getUsername, messageId)
      //      ReferenceCountUtil.safeRelease(removedMsg.payload)
    }
  }

  def remoteAddress: Optional[InetSocketAddress] = {
    if (connected) return Optional.of(connection.remoteAddress)
    Optional.empty
  }


  def cleanSessionQueue(): Unit = {
    while (messageIndexQueue.size > 0) messageIndexQueue.poll
  }

  import io.handler.codec.mqtt.MqttWillMessage

  def update(clean: Boolean, will: MqttWillMessage): Unit = {
    this.clean = clean
    this.willMsg = will
  }


  def markConnecting(): Unit = {
    assignState(SessionStatus.DISCONNECTED, SessionStatus.CONNECTING)
  }

  def completeConnection: Boolean = assignState(SessionStatus.CONNECTING, SessionStatus.CONNECTED)

  def bind(connection: IConnection): Unit = {
    this.connection = connection
  }

  def bindUdpInetSocketAddress(inetSocketAddress: InetSocketAddress): Unit = {
    this.udpInetSocketAddress = inetSocketAddress
  }

  def getUdpInetSocketAddress: InetSocketAddress = this.udpInetSocketAddress

  def disconnected: Boolean = status.get eq SessionStatus.DISCONNECTED


  def getClientId: String = clientId

  def getSubscriptions = new util.ArrayList[Subscription](subscriptions)

  /**
   * uddate the status of session
   *
   * @param expected expected Status
   * @param newState new Status
   * @return
   */
  def assignState(expected: SessionStatus, newState: SessionStatus): Boolean = status.compareAndSet(expected, newState)

  def hasWill: Boolean = willMsg != null

  def getWill: MqttWillMessage = willMsg

  def closeImmediately(): Unit = {
    connection.dropConnection()
  }

  def disconnect(): Unit = {
    val res = assignState(SessionStatus.CONNECTED, SessionStatus.DISCONNECTING)
    if (!res) {
      logger.info("this status is SessionStatus.DISCONNECTING")
      return
    }
    connection = null
    willMsg = null
    assignState(SessionStatus.DISCONNECTING, SessionStatus.DISCONNECTED)
  }

  def isClean: Boolean = clean


  // consume the queue
  private def drainQueueToConnection(): Unit = {
    drainQueueService.submit(new DrainQueueWorker)
  }

  /**
   *
   * doDrainQueueToConnection
   */
  private def doDrainQueueToConnection(): Unit = {
    reSendInflictNotAcked()
    while (!messageIndexQueue.isEmpty && inflictHasSlotsAndConnectionIsUp) {
      val msgIndex = messageIndexQueue.poll()
      if (!ObjectUtils.isEmpty(msgIndex)) {

        //notify: there must not use foreach(), there queue not implement
        //  @Override
        //    public Iterator<MsgIndex> iterator() {
        //        return null;
        //    }
        //and inflictHasSlotsAndConnectionIsUp is true
        val msg = msgQueue.poll(new SearchData(clientId, msgIndex))
        if (!ObjectUtils.isEmpty(msg)) {
          msg.getMsg match {
            case mqttMessage: MqttMessage =>
              mqttMessage.fixedHeader.messageType match {
                case MqttMessageType.CUSTOMER =>
                case MqttMessageType.PUBLISH =>
                  val msgPub = mqttMessage.asInstanceOf[MqttPublishMessage]
                  msgPub.variableHeader.setPacketId(nextPacketId)
                  if (msgPub.fixedHeader.qosLevel ne MqttQoS.AT_MOST_ONCE) {
                    inflictSlots.decrementAndGet
                    val old = inflictWindow.put(msgPub.variableHeader.packetId, msgPub.copy)
                    ReferenceCountUtil.safeRelease(old)
                    inflictSlots.incrementAndGet
                    inflictTimeouts.add(new InFlightPacket(msgPub.variableHeader.packetId, FLIGHT_BEFORE_RESEND_MS))
                  }
                  connection.sendPublish(msgPub)

                case MqttMessageType.PUBACK =>
                case MqttMessageType.PUBREC =>
                case MqttMessageType.PUBREL =>
                case MqttMessageType.PUBCOMP =>
                  val variableHeader = mqttMessage.variableHeader.asInstanceOf[MqttMessageIdVariableHeader]
                  inflictSlots.decrementAndGet
                  val packetId = variableHeader.messageId
                  val old = inflictWindow.put(packetId, mqttMessage)
                  ReferenceCountUtil.safeRelease(old)
                  inflictSlots.incrementAndGet
                  inflictTimeouts.add(new InFlightPacket(packetId, FLIGHT_BEFORE_RESEND_MS))
                  val pubRel = MqttMessageFactory.newPubRelMessage(packetId)
                  connection.sendIfWritableElseDrop(pubRel)

                case _ =>

              }

            case _ => logger.trace("error msg {}", msg)
          }
          ReferenceCountUtil.safeRelease(msg.getMsg)
        }
      }
    }
  }


  def reSendInflictNotAcked(): Unit = {
    if (this.inflictTimeouts.size == 0) inflictWindow.clear()
    else {
      val expired = new util.ArrayList[InFlightPacket](inflictWindowSize)
      this.inflictTimeouts.drainTo(expired)
      debugLogPacketIds(expired)
      expired.forEach(notAckPacketId => {
        if (inflictWindow.containsKey(notAckPacketId.getPacketId)) {
          val message = inflictWindow.remove(notAckPacketId.getPacketId)
          if (null == message) { // Already acked...
            logger.warn("Already acked...")
          } else message match {
            case pubRelMsg: MqttPubRelMessage => connection.sendIfWritableElseDrop(pubRelMsg)
            case publishMsg: MqttPublishMessage =>
              //                        MqttPublishMessage publishMsg = publishNotRetainedDuplicated(notAckPacketId, msg.getTopic(), msg.getPublishingQos(), msg.getPayload());
              inflictTimeouts.add(new InFlightPacket(notAckPacketId.getPacketId, FLIGHT_BEFORE_RESEND_MS))
              connection.sendPublish(publishMsg)
            case _ => logger.warn("Already acked...")
          }
          ReferenceCountUtil.safeRelease(message)
        }
      })
    }
  }

  private def inflictHasSlotsAndConnectionIsUp = inflictSlots.get > 0 && connected && connection.getChannel.isWritable


  def connected: Boolean = status.get == SessionStatus.CONNECTED

  private def canSkipQueue = messageIndexQueue.isEmpty && inflictSlots.get > 0 && connected && connection.getChannel.isWritable


  private def debugLogPacketIds(expired: util.Collection[InFlightPacket]): Unit = {
    if (!logger.isDebugEnabled || expired.isEmpty) return
    val sb = new StringBuilder
    expired.forEach(packet => {
      sb.append(packet.getPacketId).append(", ")
    })
    logger.debug("Resending {} in flight packets [{}]", expired.size, sb)
  }


  override def toString: String = "Session {" + "clientId='" + clientId + '\'' + ", clean=" + clean + ", status=" + status + ", inflightSlots=" + inflictSlots + '}'


  class DrainQueueWorker extends Runnable {
    override def run(): Unit = {
      try
        doDrainQueueToConnection()
      catch {
        case _: Exception =>
      }
    }
  }

  /**
   * 接收到一个消息
   *
   * @param msg msg；
   */
  override def receiveMessage(msg: Message): Boolean = {
    //TODO
    false
  }

  /**
   * 发送消息
   *
   * @param msg 消息
   * @return 是否发送成功
   */
  override def sendMessage(msg: Message): Boolean = {
    //TODO
    false
  }

  /**
   * sub
   *
   * @param subscription sbu
   */
  override def subscription(subscription: Subscription): Unit = {

    //TODO
  }

  /**
   * subscription
   *
   * @param subscription sub
   */
  override def unSubscription(subscription: Subscription): Unit = {

    //TODO
  }


  def nextPacketId: Int = {
    if (lastPacketId.incrementAndGet > 65535) {
      lastPacketId.set(1)
      return 1
    }
    lastPacketId.get
  }
}
