package streams

import java.util.function.Supplier

import scala.collection.mutable
import scala.concurrent.ExecutionContext

/**
  * Created by Paweł Sikora.
  */
object SameThreadExecutionContext extends ExecutionContext {
  val queue = ThreadLocal.withInitial(new Supplier[mutable.Queue[Runnable]] {
    override def get(): mutable.Queue[Runnable] = new mutable.Queue[Runnable]()
  })

  override def execute(runnable: Runnable): Unit = {
    val queueTL: mutable.Queue[Runnable] = queue.get()
    queueTL.enqueue(runnable)
    while (queueTL.nonEmpty) {
      queueTL.dequeue().run()
    }
  }

  override def reportFailure(cause: Throwable): Unit = cause.printStackTrace()
}