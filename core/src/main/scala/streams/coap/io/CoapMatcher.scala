package streams.coap.io

import monifu.reactive.Ack.Continue
import monifu.reactive.OverflowStrategy.BackPressure
import monifu.reactive.observers.BufferedSubscriber
import monifu.reactive.{Ack, Observer, Subscriber}
import streams.SameThreadExecutionContext
import streams.bidi.{Bidirectional, BidirectionalObserver}
import streams.coap.core.message.CoapMessage.IsRequest
import streams.coap.core.{CoapEnvelope, CoapMessageProcessor, IncomingMessageEnvelope, OutgoingEnvelope}

import scala.concurrent.Future

/**
  * Created by Paweł Sikora.
  */
object CoapMatcher {

  type OutgoingEnvelopeT = OutgoingEnvelope[Observer[IncomingMessageEnvelope]]
  type IncomingEnvelopeT = IncomingMessageEnvelope

  def apply[I, O](source: Bidirectional[I, O])(implicit ev: I <:< IncomingMessageEnvelope, ev2: CoapEnvelope <:< O): Bidirectional[IncomingEnvelopeT, OutgoingEnvelopeT] = {
    Bidirectional.create[IncomingEnvelopeT, OutgoingEnvelopeT] { subscription =>
      source.onSubscribe(new BidirectionalObserver[I, O] {

        override def connect(messagesSink: Observer[O]): Observer[I] = {
          val safeSink = messagesSink match {
            case b: BufferedSubscriber[O] => b
            case _ => println("wraping subscirber")
              BufferedSubscriber(Subscriber(messagesSink, subscription.scheduler), BackPressure(2048))
          }
          val processor = new CoapMessageProcessor[Observer[IncomingMessageEnvelope]]()
          // Source 1
          val outgoingListener = new Observer[OutgoingEnvelopeT] {
            override def onNext(elem: OutgoingEnvelopeT): Future[Ack] = {
              processor.send(elem)
              safeSink.onNext(elem)
            }

            override def onError(ex: Throwable): Unit = subscription.scheduler.reportFailure(ex)

            override def onComplete(): Unit = println("completed")
          }
          val requestsObserver: Observer[IncomingEnvelopeT] = subscription.connect(outgoingListener)

          // Source 2
          val incomingMessages = new Observer[I] {
            override def onError(ex: Throwable): Unit = subscription.scheduler.reportFailure(ex)

            override def onComplete(): Unit = println("completed")

            override def onNext(elem: I): Future[Ack] = {
              val result = processor.receive(elem)
              val ansFut = result.answer.map(ans => safeSink.onNext(ans)).getOrElse(Continue)
              val cbfut = result.matchedEnvelope.map(env => env.exchange.onNext(elem)).getOrElse(Continue)
              val reqFut = elem.message match {
                case IsRequest(r) => requestsObserver.onNext(elem)
                case _ => Continue
              }
              if (ansFut == Continue && cbfut == Continue && reqFut == Continue) {
                Continue
              } else {
                implicit val ec = SameThreadExecutionContext
                ansFut.flatMap(_ => cbfut).flatMap(_ => reqFut)
              }
            }
          }
          incomingMessages
        }
      })(subscription.scheduler)
    }
  }
}
