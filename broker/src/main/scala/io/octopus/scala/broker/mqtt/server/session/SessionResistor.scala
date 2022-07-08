//package io.octopus.scala.mqtt.broker.session
//
//import io.netty.util.concurrent.DefaultThreadFactory
//import io.octopus.broker.security.ReadWriteControl
//import io.octopus.utils.ObjectUtils
//import io.octopus.exception.SessionCorruptedException
//import io.octopus.kernel.config.IConfig
//import io.octopus.kernel.message.KernelMsg
//import io.octopus.kernel.queue.{MsgIndex, MsgQueue}
//import io.octopus.kernel.repository.IQueueRepository
//import io.octopus.kernel.session.{CreationModeEnum, DefaultSession, ISession, ISessionResistor, SessionCreationResult, SessionStatus}
//import io.octopus.kernel.subscriptions.Topic
//import io.octopus.scala.mqtt.broker.PostOffice
//import io.octopus.scala.mqtt.persistence.MemoryQueue
//import io.store.persistence.disk.CheckPointServer
//import org.slf4j.{Logger, LoggerFactory}
//
//import java.util
//import java.util.concurrent._
//
//
//class SessionResistor(queueRepository: IQueueRepository,
//                      authorizator: ReadWriteControl, config: IConfig, checkPointServer: CheckPointServer) extends ISessionResistor {
//
//
//  private val logger: Logger = LoggerFactory.getLogger(classOf[SessionResistor])
//
//  private val sessions: ConcurrentHashMap[String, DefaultSession] = new ConcurrentHashMap[String, DefaultSession]()
//
//  private val usernamePools: ConcurrentHashMap[String, util.HashSet[String]] = new ConcurrentHashMap[String, util.HashSet[String]]()
//
//  private val indexQueues: ConcurrentHashMap[String, util.Queue[MsgIndex]] = new ConcurrentHashMap[String, util.Queue[MsgIndex]]()
//
//  private val receiveMaximum: Integer = 10
//  private var postOffice: PostOffice = _
//
//  private val msgQueue:MsgQueue[KernelMsg] = new MemoryQueue(config,checkPointServer)
//
//  private val drainQueueService: ExecutorService = new ThreadPoolExecutor(4, 4,
//    0L, TimeUnit.MILLISECONDS,
//    new LinkedBlockingQueue[Runnable](9999),
//    new DefaultThreadFactory("drain-queue"),
//    new ThreadPoolExecutor.CallerRunsPolicy(),
//  )
//
//  def setPostOffice(postOffice: PostOffice): Unit = {
//    this.postOffice = postOffice;
//  }
//
//
//  def createOrReOpenSession(clientId: String, username: String, isClean: Boolean, willMsg: KernelMsg, clientVersion: Int): SessionCreationResult = {
//
//    var createResult: SessionCreationResult = null
//    val newSession = createNewSession(clientId, username, isClean, willMsg, clientVersion)
//    if (!sessions.contains(clientId)) {
//      createResult = new SessionCreationResult(newSession, CreationModeEnum.CREATED_CLEAN_NEW, false)
//
//      val previous: DefaultSession = sessions.putIfAbsent(clientId, newSession)
//      val success: Boolean = previous == null
//      // new session
//      if (success) logger.trace("case 1, not existing session with CId {}", clientId)
//      else { //old session
//        createResult = reOpenExistingSession(clientId, newSession, username, isClean, willMsg)
//      }
//    } else {
//      createResult = reOpenExistingSession(clientId, newSession, username, isClean, willMsg)
//    }
//    createResult
//  }
//
//
//  def reOpenExistingSession(clientId: String, newSession: DefaultSession, username: String, newClean: Boolean, willMsg: KernelMsg): SessionCreationResult = {
//    val oldSession: DefaultSession = sessions.get(clientId)
//    var result: SessionCreationResult = null
//    if (oldSession.disconnected) {
//      if (newClean) {
//        val updatedStatus = oldSession.assignState(SessionStatus.DISCONNECTED, SessionStatus.CONNECTING)
//        if (!updatedStatus) {
//          throw new SessionCorruptedException("old session was already changed state")
//        }
//        // case 2
//        // publish new session
//        dropQueuesForClient(clientId)
//        unsubscribe(oldSession)
//        copySessionConfig(newClean, willMsg, oldSession)
//        logger.trace("case 2, oldSession with same CId {} disconnected", clientId)
//        result = new SessionCreationResult(oldSession, CreationModeEnum.CREATED_CLEAN_NEW, true)
//      } else {
//        val connecting = oldSession.assignState(SessionStatus.DISCONNECTED, SessionStatus.CONNECTING)
//        if (!connecting) throw new SessionCorruptedException("old session moved in connected state by other thread")
//        // case 3
//        reactivateSubscriptions(oldSession, username)
//
//        logger.trace("case 3, oldSession with same CId {} disconnected", clientId)
//        result = new SessionCreationResult(oldSession, CreationModeEnum.REOPEN_EXISTING, true)
//      }
//    } else {
//      // case 4
//      logger.trace("case 4, oldSession with same CId {} still connected, force to close", clientId)
//      oldSession.closeImmediately()
//      //remove(clientId);
//      result = new SessionCreationResult(newSession, CreationModeEnum.DROP_EXISTING, true)
//    }
//    if (!newClean) { //把消息分发给新的session
//      newSession.addInflictWindow(oldSession.getInflictWindow)
//    }
//    var published = false
//    if (result.getMode != CreationModeEnum.REOPEN_EXISTING) {
//      logger.debug("Drop session of already connected client with same id")
//      published = sessions.replace(clientId, oldSession, newSession)
//    }
//    else {
//      logger.debug("Replace session of client with same id")
//      published = sessions.replace(clientId, oldSession, oldSession)
//    }
//    if (!published) throw new SessionCorruptedException("old session was already removed")
//
//    result
//  }
//
//
//  /**
//   * create new session
//   *
//   * @param clientId client
//   * @return session
//   */
//  def createNewSession(clientId: String, username: String, isClean: Boolean, willMsg: KernelMsg, clientVersion: Int): DefaultSession = {
//    val sessionIndexQueue: util.Queue[MsgIndex] = indexQueues.computeIfAbsent(clientId, clientId => queueRepository.createQueue(clientId, isClean))
//    val newSession: DefaultSession = new DefaultSession(postOffice, clientId, username, isClean, willMsg, sessionIndexQueue, receiveMaximum, clientVersion, msgQueue, drainQueueService)
//    newSession.markConnecting()
//    newSession
//  }
//
//
//  /**
//   * drop client index queue
//   *
//   * @param clientId client
//   */
//  private def dropQueuesForClient(clientId: String): Unit = {
//    indexQueues.remove(clientId)
//  }
//
//  private def reactivateSubscriptions(session: DefaultSession, username: String): Unit = { //verify if subscription still satisfy read ACL permissions
//    session.getSubTopicList.forEach(topicStr => {
//      val topicReadable = authorizator.canRead(new Topic(topicStr), username, session.getClientId)
//      if (!topicReadable) {
//        postOffice.unSubscriptions(session, session.getSubTopicList)
//      }
//    })
//  }
//
//
//  /**
//   *
//   * @param session instance of session {@link io.octopus.kernel.session.DefaultSession}
//   */
//  private def unsubscribe(session: ISession): Unit = {
//    postOffice.unSubscriptions(session, session.getSubTopicList)
//    //    session.getSubTopicList.forEach(existingSub => subscriptionsDirectory.removeSubscription(new Topic(existingSub), session.getClientId))
//  }
//
//
//  /**
//   * copy session config (isClean,will)
//   *
//   * @param msg     msg
//   * @param session session
//   */
//  private def copySessionConfig(isClean: Boolean, willMessage: KernelMsg, session: DefaultSession): Unit = {
//    session.update(isClean, willMessage)
//  }
//
//
//  def registerUserName(username: String, clientId: String): Unit = {
//    val pool: util.HashSet[String] = usernamePools.computeIfAbsent(username, _ => new util.HashSet[String]())
//    pool.add(clientId)
//  }
//
//  def unRegisterClientIdByUsername(username: String, clientId: String): Unit = {
//    val userClient: util.Set[String] = usernamePools.get(username)
//    if (ObjectUtils.isEmpty(userClient)) {
//      return
//    }
//    try userClient.remove(clientId)
//    catch {
//      case e: Exception => e.printStackTrace()
//
//    }
//    if (userClient.size == 0) usernamePools.remove(username)
//  }
//
//  def getClientIdByUsername(username: String): util.HashSet[String] = usernamePools.get(username)
//
//
//  /**
//   * get all session
//   *
//   * @return sessions
//   */
//  def getAllClientId: util.Set[String] = sessions.keySet
//
//
//  override def retrieve(clientID: String): DefaultSession = sessions.get(clientID)
//
//
//  def remove(session: DefaultSession): Unit = {
//    sessions.remove(session.getClientId, session)
//    queueRepository.cleanQueue(session.getClientId)
//  }
//}
