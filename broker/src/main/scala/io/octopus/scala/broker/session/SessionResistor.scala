package io.octopus.scala.broker.session

import io.handler.codec.mqtt.{IMessage, MqttConnectMessage, MqttQoS, MqttWillMessage}
import io.netty.buffer.Unpooled
import io.netty.channel.Channel
import io.netty.util.concurrent.DefaultThreadFactory
import io.octopus.base.interfaces.{IQueueRepository, ISessionResistor}
import io.octopus.base.queue.{MsgIndex, MsgQueue}
import io.octopus.base.subscriptions.ISubscriptionsDirectory
import io.octopus.broker.exception.SessionCorruptedException
import io.octopus.broker.security.ReadWriteControl
import io.octopus.broker.session.{CreationModeEnum, SessionStatus}
import io.octopus.scala.broker.{IConnection, MQTTConnection}
import org.slf4j.{Logger, LoggerFactory}

import java.util
import java.util.concurrent._


class SessionResistor(subscriptionsDirectory: ISubscriptionsDirectory, queueRepository: IQueueRepository,
                      authorizator: ReadWriteControl, storeService: MsgQueue[IMessage]) extends ISessionResistor{

  private val logger: Logger = LoggerFactory.getLogger(classOf[SessionResistor])

  private var udpChannel: Channel = _

  private val sessions: ConcurrentHashMap[String, Session] = new ConcurrentHashMap[String, Session]()

  private val usernamePools: ConcurrentHashMap[String, util.HashSet[String]] = new ConcurrentHashMap[String, util.HashSet[String]]()

  private val indexQueues: ConcurrentHashMap[String, util.Queue[MsgIndex]] = new ConcurrentHashMap[String, util.Queue[MsgIndex]]()

  private val receiveMaximum: Integer = 10

  private val drainQueueService: ExecutorService =new ThreadPoolExecutor(4, 4,
    0L, TimeUnit.MILLISECONDS,
    new LinkedBlockingQueue[Runnable](9999),
    new DefaultThreadFactory("drain-queue"),
    new ThreadPoolExecutor.CallerRunsPolicy(),
  )


  def createOrReOpenSession(msg: MqttConnectMessage, connection:IConnection, clientId: String, username: String): SessionCreationResult = {

    var createResult: SessionCreationResult = null
    val newSession = createNewSession(msg, clientId)
    if (!sessions.contains(clientId)) {
      createResult = new SessionCreationResult(newSession, CreationModeEnum.CREATED_CLEAN_NEW, false)

      val previous: Session = sessions.putIfAbsent(clientId, newSession)
      val success: Boolean = previous == null
      // new session
      if (success) logger.trace("case 1, not existing session with CId {}", clientId)
      else { //old session
        createResult = reOpenExistingSession(msg, clientId, newSession, username)
      }
    } else {
      createResult = reOpenExistingSession(msg, clientId, newSession, username)
    }
    createResult
  }


  def reOpenExistingSession(msg: MqttConnectMessage, clientId: String, newSession: Session, username: String): SessionCreationResult = {
    val newClean: Boolean = msg.variableHeader().isCleanSession
    val oldSession: Session = sessions.get(clientId)
    var result: SessionCreationResult = null
    if (oldSession.disconnected) {
      if (newClean) {
        val updatedStatus = oldSession.assignState(SessionStatus.DISCONNECTED, SessionStatus.CONNECTING)
        if (!updatedStatus) {
          throw new SessionCorruptedException("old session was already changed state")
        }
        // case 2
        // publish new session
        dropQueuesForClient(clientId)
        unsubscribe(oldSession)
        copySessionConfig(msg, oldSession)
        logger.trace("case 2, oldSession with same CId {} disconnected", clientId)
        result = new SessionCreationResult(oldSession, CreationModeEnum.CREATED_CLEAN_NEW, true)
      } else {
        val connecting = oldSession.assignState(SessionStatus.DISCONNECTED, SessionStatus.CONNECTING)
        if (!connecting) throw new SessionCorruptedException("old session moved in connected state by other thread")
        // case 3
        reactivateSubscriptions(oldSession, username)

        logger.trace("case 3, oldSession with same CId {} disconnected", clientId)
        result = new SessionCreationResult(oldSession, CreationModeEnum.REOPEN_EXISTING, true)
      }
    } else {
      // case 4
      logger.trace("case 4, oldSession with same CId {} still connected, force to close", clientId)
      oldSession.closeImmediately()
      //remove(clientId);
      result = new SessionCreationResult(newSession, CreationModeEnum.DROP_EXISTING, true)
    }
    if (!msg.variableHeader.isCleanSession) { //把消息分发给新的session
      newSession.addInflictWindow(oldSession.getInflictWindow)
    }
    var published = false
    if (result.getMode != CreationModeEnum.REOPEN_EXISTING) {
      logger.debug("Drop session of already connected client with same id")
      published = sessions.replace(clientId, oldSession, newSession)
    }
    else {
      logger.debug("Replace session of client with same id")
      published = sessions.replace(clientId, oldSession, oldSession)
    }
    if (!published) throw new SessionCorruptedException("old session was already removed")

    result
  }


  /**
   * create new session
   *
   * @param msg      msg
   * @param clientId client
   * @return session
   */
  def createNewSession(msg: MqttConnectMessage, clientId: String): Session = {
    val clean: Boolean = msg.variableHeader().isCleanSession
    val sessionIndexQueue: util.Queue[MsgIndex] = indexQueues.computeIfAbsent(clientId, clientId => queueRepository.createQueue(clientId, clean))
    var newSession: Session = null
    if (msg.variableHeader().isWillFlag) {
      val willMsg: MqttWillMessage = createWillMsg(msg)
      newSession = new Session(clientId, clean, willMsg, sessionIndexQueue, receiveMaximum, msg.variableHeader().version(), storeService,drainQueueService)
    } else {
      newSession = new Session(clientId, clean,null, sessionIndexQueue, receiveMaximum, msg.variableHeader().version(), storeService,drainQueueService)
    }
    newSession.markConnecting()
    newSession
  }


  /**
   * create will msg
   *
   * @param msg mqttConnectMessage
   * @return
   */
  def createWillMsg(msg: MqttConnectMessage): MqttWillMessage = {
    val willPayload = Unpooled.copiedBuffer(msg.payload.willMessageInBytes)
    val willTopic = msg.payload.willTopic
    val retained = msg.variableHeader.isWillRetain
    val qos = MqttQoS.valueOf(msg.variableHeader.willQos)
    new MqttWillMessage(willTopic, willPayload, qos, retained)
  }


  /**
   * drop client index queue
   *
   * @param clientId client
   */
  private def dropQueuesForClient(clientId: String): Unit = {
    indexQueues.remove(clientId)
  }

  private def reactivateSubscriptions(session: Session, username: String): Unit = { //verify if subscription still satisfy read ACL permissions
    for (i <- 0 until session.getSubscriptions.size()) {
      val topicReadable = authorizator.canRead(session.getSubscriptions.get(i).getTopicFilter, username, session.getClientId)
      if (!topicReadable) subscriptionsDirectory.removeSubscription(session.getSubscriptions.get(i).getTopicFilter, session.getClientId)

      // TODO
      //            subscriptionsDirectory.reactivate(existingSub.getTopicFilter(), session.getClientID());
    }
  }



  /**
   *
   * @param session  instance of session {@link io.octopus.scala.broker.session.Session}
   */
  private def unsubscribe(session: Session): Unit = {
    session.getSubscriptions.forEach(existingSub => subscriptionsDirectory.removeSubscription(existingSub.getTopicFilter, session.getClientId))
  }


  /**
   * copy session config (isClean,will)
   *
   * @param msg     msg
   * @param session session
   */
  private def copySessionConfig(msg: MqttConnectMessage, session: Session): Unit = {
    val clean = msg.variableHeader.isCleanSession
    var will: MqttWillMessage = null
    if (msg.variableHeader.isWillFlag) {
      will = createWillMsg(msg)
    }
    else will = null
    session.update(clean, will)
  }


  def getUdpChannel: Channel = udpChannel

  def setUdpChannel(udpChannel: Channel): Unit = {
    this.udpChannel = udpChannel
  }


  def registerUserName(username: String, clientId: String): Unit = {
    val pool: util.HashSet[String] = usernamePools.computeIfAbsent(username, _ => new util.HashSet[String]())
    pool.add(clientId)
  }

  def removeUserClientIdByUsername(username: String, clientId: String): Unit = {
    val userClient: util.Set[String] = usernamePools.get(username)
    try userClient.remove(clientId)
    catch {
      case _: Exception =>

    }
    if (userClient.size == 0) usernamePools.remove(username)
  }

  def getClientIdByUsername(username: String): util.HashSet[String] = usernamePools.get(username)


  /**
   * get all session
   *
   * @return sessions
   */
  def getAllClientId: util.Set[String] = sessions.keySet


 override def retrieve(clientID: String): Session = sessions.get(clientID)


  def remove(session: Session): Unit = {
    sessions.remove(session.getClientId, session)
    queueRepository.cleanQueue(session.getClientId)
  }
}
