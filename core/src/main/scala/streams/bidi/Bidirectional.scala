package streams.bidi

import coap.CoapMatcher
import coap.core.{CoapEnvelope, IncomingMessageEnvelope}
import CoapMatcher.{IncomingEnvelopeT, OutgoingEnvelopeT}
import monifu.concurrent.Scheduler
import streams.bidi.BidirectionalSubscription.CancellationObserver
import monifu.reactive.{Ack, Observable, Observer, Subscriber, _}
import monifu.reactive.subjects.PublishSubject

import scala.concurrent.Future
import scala.util.control.NonFatal

/**
  *
  * @tparam I message we produce
  * @tparam O message we consume
  */
trait Bidirectional[+I, -O] extends Observable[I] {

  def onSubscribe(subscription: BidirectionalSubscription[I, O]): Unit

  def onSubscribe(subscription: BidirectionalObserver[I, O])(implicit scheduler: Scheduler): Unit = {
    onSubscribe(BidirectionalSubscription(subscription, scheduler))
  }

  def onSubscribe(subscriber: Subscriber[I], observable: Observable[O]): Unit = {
    onSubscribe(new BidirectionalObserver[I, O] {
      override def connect(sink: Observer[O]): Subscriber[I] = {
        observable.onSubscribe(sink)(subscriber.scheduler)
        subscriber
      }
    })(subscriber.scheduler)
  }

  def onSubscribe(subscriber: Subscriber[I]): Unit = onSubscribe(subscriber, Observable.empty)

  def shortCircuit(connector: I => O)(implicit s: Scheduler): Unit = {
    onSubscribe(new BidirectionalObserver[I, O] {
      override def connect(sink: Observer[O]): Observer[I] = new Observer[I] {
        override def onError(ex: Throwable): Unit = s.reportFailure(ex)

        override def onComplete(): Unit = sink.onComplete()

        override def onNext(elem: I): Future[Ack] = sink.onNext(connector(elem))
      }
    })
  }

  def map[I2, O2](fin: I => I2, fout: O2 => O): Bidirectional[I2, O2] = {
    Bidirectional.map(this)(fin, fout)
  }

  def coapMatcher(implicit ev: I <:< IncomingMessageEnvelope, ev2: CoapEnvelope <:< O): Bidirectional[IncomingEnvelopeT, OutgoingEnvelopeT] = {
    CoapMatcher(this)
  }

  def write(writer: Observable[O])(implicit s: Scheduler) = {
    onSubscribe(new BidirectionalObserver[I, O] {
      override def connect(output: Observer[O]): Observer[I] = {
        writer.onSubscribe(output)
        new CancellationObserver(s)
      }
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

  def mapIn[I, O, I2](source: Bidirectional[I, O])(f: I => I2): Bidirectional[I2, O] = {
    map(source)(f, identity)
  }

  def mapOut[I, O, O2](source: Bidirectional[I, O])(f: O2 => O): Bidirectional[I, O2] = {
    map(source)(identity, f)
  }

  def map[I, O, I2, O2](source: Bidirectional[I, O])(fin: I => I2, fout: O2 => O): Bidirectional[I2, O2] = {
    Bidirectional.create[I2, O2] { subscription =>
      source.onSubscribe(new BidirectionalObserver[I, O] {
        override def connect(sink: Observer[O]): Observer[I] = {
          val underlying = subscription.connect(new Observer[O2] {
            override def onError(ex: Throwable): Unit = sink.onError(ex)

            override def onComplete(): Unit = sink.onComplete()

            override def onNext(elem: O2): Future[Ack] = sink.onNext(fout(elem))
          })
          new Observer[I] {
            override def onError(ex: Throwable): Unit = underlying.onError(ex)

            override def onComplete(): Unit = underlying.onComplete()

            override def onNext(elem: I): Future[Ack] = underlying.onNext(fin(elem))
          }
        }
      })(subscription.scheduler)
    }
  }

  def unMergeOut[I, O](source: Bidirectional[I, O])(overflowStrategy: OverflowStrategy): Bidirectional[I, Observable[O]] = {
    Bidirectional.create[I, Observable[O]] { subscription =>
      source.onSubscribe(new BidirectionalObserver[I, O] {

        override def connect(sink: Observer[O]): Observer[I] = {
          val transformation = PublishSubject[Observable[O]]()
          transformation.merge(overflowStrategy).onSubscribe(sink)(subscription.scheduler)
          subscription.connect(transformation)
        }
      })(subscription.scheduler)
    }
  }
}