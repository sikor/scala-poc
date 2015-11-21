package streams.bidi

import monifu.concurrent.Scheduler
import monifu.reactive.Ack.Cancel
import monifu.reactive.{Ack, Observer}

import scala.concurrent.Future

/**
  * Created by Paweł Sikora.
  */

/**
  * We want to be sure that our Observable has sink before we start to receive messages.
  * So we return our Observer only after we'v got subscriber.
  *
  * @tparam I message we consume
  * @tparam O message we produce
  */
trait BidirectionalSubscription[-I, +O] {

  implicit def scheduler: Scheduler

  /**
    *
    * @param onSubscribe this is the subscriber to our internal Observable.
    * @return return our subscriber
    */
  def connect(onSubscribe: Observer[O]): Observer[I]


}

object BidirectionalSubscription {

  class CancelObserver(scheduler: Scheduler) extends Observer[Any] {
    override def onNext(elem: Any): Future[Ack] = Cancel

    override def onError(ex: Throwable): Unit = scheduler.reportFailure(ex)

    override def onComplete(): Unit = {}
  }

}