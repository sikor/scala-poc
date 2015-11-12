package streams.benchmarks

import monifu.reactive.{Observer, Ack}
import monifu.reactive.Ack.Continue

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{Await, Promise, Future}

/**
  * Created by Paweł Sikora.
  */
class AwaitableObserver(onNextFunc: Any => Future[Ack] = _ => Continue) extends Observer[Any] {
  private val completed = Promise[Unit]

  def onFinished: Future[Unit] = completed.future

  def await(duration: FiniteDuration): Unit = {
    // await for result to throw exception if any occurred
    Await.result(onFinished, duration)
  }

  override def onNext(elem: Any): Future[Ack] = onNextFunc(elem)

  override def onError(ex: Throwable): Unit = completed.failure(ex)

  override def onComplete(): Unit = completed.success(())
}
