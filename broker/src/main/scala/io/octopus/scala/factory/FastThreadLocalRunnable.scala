package io.octopus.scala.factory

import io.netty.util.concurrent.FastThreadLocal

/**
 * @author chenxu
 * @version 1
 */

class FastThreadLocalRunnable(runnable: Runnable) extends Runnable {

  override def run(): Unit = {
    try this.runnable.run()
    finally FastThreadLocal.removeAll()
  }

}

object FastThreadLocalRunnable {
  def wrap(runnable: Runnable) = if (runnable.isInstanceOf[FastThreadLocalRunnable]) runnable
  else new FastThreadLocalRunnable(runnable)
}
