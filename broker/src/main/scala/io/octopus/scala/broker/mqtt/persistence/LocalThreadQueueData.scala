package io.octopus.scala.broker.mqtt.persistence

import io.octopus.kernel.kernel.queue.SearchData

import java.util

/**
 * @author chenxu
 * @date $date$ $time$
 * @version 1
 */

class LocalThreadQueueData(localThread: Thread, searchDataQueue:util.Queue[SearchData]) {


  def getSearchDataQueue():util.Queue[SearchData]= searchDataQueue

  def getLocalThread():Thread = localThread

}
