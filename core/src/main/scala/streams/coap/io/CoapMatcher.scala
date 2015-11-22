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
  type CoapServer = IncomingMessageEnvelope

  def apply(source: Bidirectional[IncomingMessageEnvelope, CoapEnvelope]): Bidirectional[CoapServer, OutgoingEnvelopeT] = {
    Bidirectional.create[CoapServer, OutgoingEnvelopeT] { subscription =>
      source.onSubscribe(new BidirectionalObserver[IncomingMessageEnvelope, CoapEnvelope] {

        override def connect(messagesSink: Observer[CoapEnvelope]): Observer[IncomingMessageEnvelope] = {
          val safeSink = messagesSink match {
            case b: BufferedSubscriber[CoapEnvelope] => b
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
          val requestsObserver: Observer[CoapServer] = subscription.connect(outgoingListener)

          // Source 2
          val incomingMessages = new Observer[IncomingMessageEnvelope] {
            override def onError(ex: Throwable): Unit = subscription.scheduler.reportFailure(ex)

            override def onComplete(): Unit = println("completed")

            override def onNext(elem: IncomingMessageEnvelope): Future[Ack] = {
              val result = processor.receive(elem)
              val ansFut = result.answer.map(ans => safeSink.onNext(ans))
              val cbfut = result.matchedEnvelope.map(env => env.exchange.onNext(elem))
              val reqFut = elem.message match {
                case IsRequest(r) => requestsObserver.onNext(elem)
                case _ => Continue
              }
              implicit val ec = SameThreadExecutionContext
              ansFut.getOrElse(Continue).flatMap(_ => cbfut.getOrElse(Continue)).flatMap(_ => reqFut)
            }
          }
          incomingMessages
        }
      })(subscription.scheduler)
    }
  }
}
