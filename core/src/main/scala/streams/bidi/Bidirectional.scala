package streams.bidi

import monifu.concurrent.Scheduler
import monifu.reactive.{Ack, Observable, Observer, Subscriber}
import streams.bidi.BidirectionalSubscription.CancellationObserver

import scala.concurrent.Future
import scala.util.control.NonFatal

/**
  *
  * @tparam I message we produce
  * @tparam O message we consume
  */
trait Bidirectional[+I, -O] extends Observable[I] {

  def onSubscribe(subscription: BidirectionalSubscription[I, O]): Unit

  def onSubscribe(subscriber: Subscriber[I], observable: Observable[O]): Unit = {
    onSubscribe(new BidirectionalSubscription[I, O] {
      override def connect(sink: Observer[O]): Subscriber[I] = {
        observable.onSubscribe(sink)(subscriber.scheduler)
        subscriber
      }

      override implicit def scheduler: Scheduler = subscriber.scheduler
    })
  }

  def onSubscribe(subscriber: Subscriber[I]): Unit = onSubscribe(subscriber, Observable.empty)

  def shortCircuit(connector: I => O)(implicit s: Scheduler): Unit = {
    onSubscribe(new BidirectionalSubscription[I, O] {
      override def connect(subscriber: Observer[O]): Observer[I] = new Observer[I] {
        override def onError(ex: Throwable): Unit = scheduler.reportFailure(ex)

        override def onComplete(): Unit = subscriber.onComplete()

        override def onNext(elem: I): Future[Ack] = subscriber.onNext(connector(elem))
      }

      override implicit def scheduler: Scheduler = s
    })
  }
}

object Bidirectional {

  def create[I, O](f: BidirectionalSubscription[I, O] => Unit): Bidirectional[I, O] = {
    new Bidirectional[I, O] {
      override def onSubscribe(subscription: BidirectionalSubscription[I, O]): Unit = {
        try {
          f(subscription)
        } catch {
          case NonFatal(e) => subscription.connect(new CancellationObserver(subscription.scheduler)).onError(e)
        }
      }
    }
  }
}