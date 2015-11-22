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
trait BidirectionalSubscription[-I, +O] extends BidirectionalObserver[I, O] {

  implicit def scheduler: Scheduler
}


object BidirectionalSubscription {

  class BidiObserverWrapper[I, O](val obs: BidirectionalObserver[I, O], val s: Scheduler) extends BidirectionalSubscription[I, O] {
    override implicit def scheduler: Scheduler = s

    override def connect(output: Observer[O]): Observer[I] = obs.connect(output)
  }

  def apply[I, O](observer: BidirectionalObserver[I, O], scheduler: Scheduler): BidirectionalSubscription[I, O] = {
    new BidiObserverWrapper[I, O](observer, scheduler)
  }

  class CancellationObserver(scheduler: Scheduler) extends Observer[Any] {
    override def onNext(elem: Any): Future[Ack] = Cancel

    override def onError(ex: Throwable): Unit = scheduler.reportFailure(ex)

    override def onComplete(): Unit = {}
  }

}