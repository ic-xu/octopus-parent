package io.octopus.scala.factory

import io.netty.util.concurrent.{DefaultThreadFactory, FastThreadLocalThread}

import java.util.concurrent.atomic.AtomicInteger

/**
 * @author chenxu
 * @version 1
 */

class FixThreadFactory(poolName: String) extends DefaultThreadFactory(poolName: String) {

  private val nextId = new AtomicInteger(0)

  override def newThread(r: Runnable): Thread = {
    val t = this.newThread(FastThreadLocalRunnable.wrap(r), this.poolName + "-" + this.nextId.incrementAndGet)
    t.setDaemon(false)
    t.setPriority(5)
    t
  }

  override protected def newThread(r: Runnable, name: String) = new FastThreadLocalThread(this.threadGroup, r, name)

}
