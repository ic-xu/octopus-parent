//package io.octopus.scala.broker.mqtt.persistence
//
//import io.octopus.config.IConfig
//import io.octopus.contants.BrokerConstants
//import io.octopus.kernel.checkpoint.CheckPoint
//import io.octopus.kernel.kernel.message.KernelPayloadMessage
//import io.octopus.kernel.kernel.queue.{MsgRepository, SearchData, StoreMsg}
//import io.store.persistence.disk.{CheckPointServer, ConcurrentFileRepository}
//import org.h2.mvstore.MVStore
//
//import java.io.IOException
//import java.util
//import java.util.concurrent._
//import java.util.concurrent.atomic.AtomicBoolean
//
///**
// * @author chenxu
// * @version 1
// */
//
//class ThreadRepository(config: IConfig, mvStore: MVStore, checkPointServer: CheckPointServer, flushDiskServiceExecutor: ScheduledExecutorService)
//  extends MsgRepository[KernelPayloadMessage] {
//
//  val autoSaveProp: String = config.getProperty(BrokerConstants.AUTOSAVE_INTERVAL_PROPERTY_NAME, "10")
//  private val autoSaveInterval = autoSaveProp.toInt
//  private val localQueue: InheritableThreadLocal[ConcurrentFileRepository] = new InheritableThreadLocal[ConcurrentFileRepository]
//  private val chileThreadMap: util.Map[String, LocalThreadQueueData] = new ConcurrentHashMap[String, LocalThreadQueueData]
//  private val queueMap: util.Map[Integer, ConcurrentFileRepository] = new ConcurrentHashMap[Integer, ConcurrentFileRepository]
////  private var dispatcher: Any = _
//  private val stopFlag: AtomicBoolean = new AtomicBoolean(false);
//  private val flushDiskServer: FlushDiskServer = new FlushDiskServer(checkPointServer, mvStore)
//
//
//  override def init(params: Any): Unit = {
////    this.dispatcher = params
////      .asInstanceOf[MsgDispatcher]
//    flushDiskServiceExecutor.scheduleWithFixedDelay(() => {
//      flushDiskServer.start(queueMap.values())
//    }, autoSaveInterval, autoSaveInterval, TimeUnit.SECONDS)
//  }
//
//
//  def getConcurrentFileQueue: ConcurrentFileRepository = {
//    val concurrentFileQueue = localQueue.get
//    if (null == concurrentFileQueue) throw new NullPointerException("concurrentFileQueue is null")
//    concurrentFileQueue
//  }
//
//
//  /**
//   *
//   * @param msg message byteArray
//   * @return the message index
//   */
//  override def offer(msg: KernelPayloadMessage): StoreMsg[KernelPayloadMessage] = {
//    var msgQueue: ConcurrentFileRepository = localQueue.get
//    if (null == msgQueue) {
//      val threadNameArray: Array[String] = Thread.currentThread.getName.split("-")
//      var threadName: Integer = null
//      //      if (threadNameArray.length > 2) {
//      //        threadName = threadNameArray(0) + "-" + threadNameArray(2)
//      //      } else {
//      //        threadName = Thread.currentThread.getName
//      //      }
//      val str = threadNameArray(threadNameArray.length - 1)
//      threadName = Integer.parseInt(str)
//      try msgQueue = new ConcurrentFileRepository(Thread.currentThread.getName, threadName, new CheckPointServer)
//      catch {
//        case e: IOException => e.printStackTrace()
//          return null
//      }
//      queueMap.put(threadName, msgQueue)
//      localQueue.set(msgQueue)
//      //TODO 发送消息和接收消息都使用同一个线程
//      //      var localThreadData: LocalThreadQueueData = chileThreadMap.get(threadName)
//      //      if (null == localThreadData) {
//      //        /**
//      //         * init dispatchMsgServer , dispatch message
//      //         */
//      //        //        singleThreadExecutor = new ThreadPoolExecutor(1, 1, 0L,
//      //        //          TimeUnit.SECONDS, new LinkedBlockingQueue[Runnable](1),
//      //        //          new DefaultThreadFactory(threadName + "-publish"),
//      //        //          new ThreadPoolExecutor.DiscardPolicy)
//      //        val indexQueue = new LinkedBlockingQueue[SearchData](9999)
//      //        val localThread = new Thread(new PullMessageWorker(dispatcher, msgQueue, indexQueue, stopFlag))
//      //        localThread.setName(threadName)
//      //        localThread.start()
//      //        localThreadData =  new LocalThreadQueueData(localThread, indexQueue)
//      //        chileThreadMap.put(threadName, localThreadData)
//      //      }
//      //      singleThreadExecutor.execute(new PullMessageWorker(dispatcher, concurrentFileQueue,stopFlag))
//    }
//    msgQueue.offer(msg)
//  }
//
//
//  /**
//   * Retrieves and removes the head of this queue,
//   * or returns {@code null} if this queue is empty.
//   *
//   * @return the head of this queue, or {@code null} if this queue is empty
//   */
//  override def poll(): StoreMsg[KernelPayloadMessage] = getConcurrentFileQueue.poll()
//
//  override def size(): Int = getConcurrentFileQueue.size()
//
//  /**
//   * Retrieves and removes the offset message of this queue,
//   * or returns {@code null} if this queue is empty.
//   *
//   * @param searchData searchData
//   * @return E
//   */
//  override def poll(searchData: SearchData): StoreMsg[KernelPayloadMessage] = {
//    //    val localThreadQueue: LocalThreadQueueData = chileThreadMap.get(searchData.getIndex.getQueueName)
//    //    if (!ObjectUtils.isEmpty(localThreadQueue)) {
//    //      localThreadQueue.getSearchDataQueue().offer(searchData)
//    //
//    //    }
//    //    null
//    val msgQueue: ConcurrentFileRepository = queueMap.get(searchData.getIndex.getQueueName)
//    msgQueue.poll(searchData)
//  }
//
//
//  /**
//   * Force message to disk
//   */
//  override def flushDisk(): Unit = getConcurrentFileQueue.flushDisk()
//
//  /**
//   * create a checkPoint in the queue
//   *
//   * @return CheckPoint
//   */
//  override def wrapperCheckPoint(): CheckPoint = getConcurrentFileQueue.wrapperCheckPoint()
//
//
//  override def stop(): Unit = {
//    stopFlag.set(true)
//    //    CHILD_THREAD_MAP.forEach((_, value) => value.shutdown())
//  }
//
//  /**
//   *
//   * @throws Exception
//   */
//  override def init(): Unit = {
//
//  }
//
//  /**
//   * 方法销毁之后调用
//   */
//  override def destroy(): Unit = {
//
//  }
//}
