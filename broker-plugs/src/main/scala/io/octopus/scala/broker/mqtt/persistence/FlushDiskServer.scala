package io.octopus.scala.broker.mqtt.persistence

import io.octopus.kernel.utils.ObjectUtils
import io.store.persistence.disk.{CheckPointServer, ConcurrentFileRepository}
import org.h2.mvstore.MVStore
import org.slf4j.{Logger, LoggerFactory}

import java.text.SimpleDateFormat

/**
 * @author chenxu
 * @version 1
 */

class FlushDiskServer(checkPointServer: CheckPointServer, mvStore: MVStore) {

  val logger: Logger = LoggerFactory.getLogger(classOf[FlushDiskServer])
  private val dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS")


  def start(queues: java.util.Collection[ConcurrentFileRepository]): Unit = {
    val queueIterator = queues.iterator()
    while (queueIterator.hasNext) {
      val queue = queueIterator.next()
      if (!ObjectUtils.isEmpty(queue)) {
//        logger.trace("{}  flush ot disk", dateFormat.format(System.currentTimeMillis))
        queue.flushDisk()

//        logger.trace("{}  save checkPoint file to disk", dateFormat.format(System.currentTimeMillis))
        checkPointServer.saveCheckPoint(queue.wrapperCheckPoint(), true)
      }
      if (null != mvStore) {
//        logger.trace("{}  commit to h2", dateFormat.format(System.currentTimeMillis))
        mvStore.commit
      }
    }
  }

}
