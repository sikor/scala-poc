package streams

import monifu.reactive.Ack.Continue
import monifu.reactive.{Ack, Observer}

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{Await, Future, Promise}

/**
  * Created by Paweł Sikora.
  */

class AwaitableObserverAny(onNextFunc: Any => Future[Ack] = (_: Any) => Continue) extends AwaitableObserver[Any]

class AwaitableObserver[-T](onNextFunc: T => Future[Ack] = (_: T) => Continue) extends Observer[T] {
  private val completed = Promise[Unit]

  def onFinished: Future[Unit] = completed.future

  def await(duration: FiniteDuration): Unit = {
    // await for result to throw exception if any occurred
    Await.result(onFinished, duration)
  }

  override def onNext(elem: T): Future[Ack] = onNextFunc(elem)

  override def onError(ex: Throwable): Unit = completed.failure(ex)

  override def onComplete(): Unit = completed.success(())
}
