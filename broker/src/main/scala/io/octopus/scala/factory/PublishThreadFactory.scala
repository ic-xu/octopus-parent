package io.octopus.scala.factory


import io.netty.util.concurrent.FastThreadLocalThread

import java.util.concurrent.ThreadFactory
import java.util.concurrent.atomic.AtomicInteger

/**
 * @author chenxu
 * @version 1
 */

class PublishThreadFactory(poolName:String,threadGroup:ThreadGroup) extends ThreadFactory {

  private val nextId = new AtomicInteger(0)


  protected def newThread(r: Runnable, name: String) = new FastThreadLocalThread(this.threadGroup, r, name)

  override def newThread(r: Runnable): Thread = {
    val t = this.newThread(FastThreadLocalRunnable.wrap(r), this.poolName + "-" + this.nextId.incrementAndGet)
    t.setDaemon(false)
    t.setPriority(5)
    t
  }
}
