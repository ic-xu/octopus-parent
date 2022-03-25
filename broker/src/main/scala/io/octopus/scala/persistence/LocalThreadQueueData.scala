package io.octopus.scala.persistence

import io.octopus.base.queue.SearchData

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
