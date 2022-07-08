package io.octopus.scala.broker.mqtt.persistence

import io.octopus.kernel.checkpoint.CheckPoint
import io.octopus.kernel.kernel.config.IConfig
import io.octopus.kernel.kernel.message.KernelMsg
import io.octopus.kernel.kernel.queue.{MsgIndex, MsgQueue, SearchData, StoreMsg}
import io.store.persistence.disk.CheckPointServer

import java.util
import java.util.concurrent._
import java.util.concurrent.atomic.AtomicLong

/**
 * @author chenxu
 * @version 1
 */

class MemoryQueue(config: IConfig, checkPointServer: CheckPointServer) extends MsgQueue[KernelMsg] {

  private val index: AtomicLong = new AtomicLong(0)

  private var hader: Long = 0

  private var tail: Long = 0

  private val globalMap: util.Map[Long, KernelMsg] = new ConcurrentHashMap[Long, KernelMsg]()


  override def init(params: Any): Unit = {

  }


  /**
   *
   * @param kernelMsg message byteArray
   * @return the message index
   */
  override def offer(kernelMsg: KernelMsg): StoreMsg[KernelMsg] = {
    val msgIndex = index.incrementAndGet()
    globalMap.put(msgIndex, kernelMsg)
    tail += 1
    new StoreMsg[KernelMsg](kernelMsg, new MsgIndex(msgIndex, 0, 0))
  }


  /**
   * Retrieves and removes the head of this queue,
   * or returns {@code null} if this queue is empty.
   *
   * @return the head of this queue, or {@code null} if this queue is empty
   */
  override def poll(): StoreMsg[KernelMsg] = {
    hader = hader + 1
    if(hader>tail){
      return null
    }
    val msg = globalMap.remove(hader)
    new StoreMsg[KernelMsg](msg, new MsgIndex(hader, 0, 0))
  }

  override def size(): Int = globalMap.size()

  /**
   * Retrieves and removes the offset message of this queue,
   * or returns {@code null} if this queue is empty.
   *
   * @param searchData searchData
   * @return E
   */
  override def poll(searchData: SearchData): StoreMsg[KernelMsg] = {
    val msg = globalMap.get(searchData.getIndex.getOffset)
    new StoreMsg[KernelMsg](msg, searchData.getIndex)
  }


  /**
   * Force message to disk
   */
  override def flushDisk(): Unit = {}

  /**
   * create a checkPoint in the queue
   *
   * @return CheckPoint
   */

    //TODO 包装检测点
  override def wrapperCheckPoint(): CheckPoint = {
    ???
  }


  override def stop(): Unit = {
    globalMap.clear()
  }

  /**
   *
   * @throws Exception
   */
  override def init(): Unit = {

  }

  /**
   * 方法销毁之后调用
   */
  override def destroy(): Unit = {

  }
}
