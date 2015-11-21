package streams.coap.io

import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.util

import _root_.streams.SameThreadExecutionContext
import _root_.streams.bidi.BidiStream._
import _root_.streams.bidi.{BidiProcessor, BidiStream}
import _root_.streams.coap.core.message.CoapMessage.{AnyCoapMessage, IsRequest}
import _root_.streams.coap.core.{CoapMessageProcessor, ListenableEnvelope, NonListenableMessageEnvelope}
import _root_.streams.coap.io.CoapSessions.{RequestExchange, CoapSession, IncomingRequests}
import _root_.streams.coap.io.UdpStream.Datagram
import monifu.reactive._
import monifu.reactive.observables.ConnectableObservable
import monifu.reactive.subjects.PublishSubject
import monifu.concurrent.Implicits.globalScheduler

/**
  * Created by Paweł Sikora.
  */
object CoapSessions {
  type CoapMsg = AnyCoapMessage


  /**
    *
    * When this session is closed it won't produce any new messages. It won't accept any new datagrams.
    * So when it signalize onComplete than subscriber can be released. New session for this address will be created.
    * It takes the stream of clients. Each has associated token for message filtering. Block-wise handling is invisible
    * for client. Server for all requests can be registered. Observation Responses without associated client can be
    * accessed separately.
    *
    */
  private[CoapSessions] class CoapSession {

    val processor = new CoapProcessor
    val bidiStream = new BidiStream[Datagram, RequestExchange, RequestExchange, Observable[Datagram]](processor.onInputMessage, processor.onOutputMessage)
    val server: ConnectableObservable[RequestExchange] = bidiStream.in().behavior(null).filter(_ != null)
    val outDatagrams: ConnectableObservable[Datagram] = bidiStream.out().merge.behavior(null).filter(_ != null)
    server.connect()
    outDatagrams.connect()



    def client(): Observer[RequestExchange] = bidiStream.out()
    /**
      * @return Responses for requests, and requests from clients.
      */

    /**
      * Each session has own back-pressure for incoming datagrams.
      */
    def inDatagrams: Observer[Datagram] = bidiStream.in()

  }

  class CoapProcessor extends BidiProcessor[Datagram, RequestExchange, RequestExchange, Observable[Datagram]] {

    val processor = new CoapMessageProcessor[RequestExchange]()


    override def onInputMessage(inputMsg: Datagram): ProcessingAction[RequestExchange, Observable[Datagram]] = {
      val result = processor.receiveRawData(inputMsg.data.array(), inputMsg.address)
      result.envelope.message match {
        case IsRequest(r) =>
          val responseListener = PublishSubject[CoapMsg]()
          val datagrams = responseListener.map(coap =>
            Datagram(ByteBuffer.wrap(processor.send(NonListenableMessageEnvelope(coap, inputMsg.address)).data), inputMsg.address)
          )
          PushToBoth(new RequestExchange(inputMsg.address, r, responseListener), datagrams)
        case _ => result.receiveAction.matchedEnvelope.foreach(_.exchange.responses.onNext(result.envelope.message).onFailure {
          case t => t.printStackTrace()
        }(SameThreadExecutionContext))
          result.receiveAction.answer match {
            case Some(env) => val toSend = processor.send(env)
              PushToOutput(Observable(Datagram(ByteBuffer.wrap(toSend.data), result.envelope.address)))
            case None => NoAction
          }
      }
    }

    override def onOutputMessage(outputMsg: RequestExchange): ProcessingAction[RequestExchange, Observable[Datagram]] = {
      val result = processor.send(ListenableEnvelope(outputMsg.request, outputMsg.address, outputMsg))
      PushToOutput(Observable(Datagram(ByteBuffer.wrap(result.data), outputMsg.address)))
    }

  }

  class IncomingRequests(val address: InetSocketAddress, val requests: Observable[RequestExchange])

  class RequestExchange(val address: InetSocketAddress, val request: CoapMsg, val responses: Observer[CoapMsg])

}

class CoapSessions extends BidiProcessor[Datagram, IncomingRequests, RequestExchange, Observable[Datagram]] {

  private val sessions = new util.HashMap[InetSocketAddress, CoapSession]

  /**
    *
    * @param datagram input datagram from lower layer
    * @return IncomingRequests which is stream of requests from one IP:PORT. Request has associated response channel.
    *         If request has many parts (block-wise) than it may not be pushed to subscriber immediately after subscription.
    *         Nevertheless, the notification about incoming block-wise transfer has to be pushed to run handling on different thread.
    *         When returned Observable completes and new request arrive from this endpoint than new Observable for this
    *         endpoint will be returned. Resulting Observable[Observable[Request]\] can be merged and requests handled
    *         separately.
    *
    */
  override def onInputMessage(datagram: Datagram): ProcessingAction[IncomingRequests, Observable[Datagram]] = {
    val oldSession = sessions.get(datagram.address)
    if (oldSession != null) {
      oldSession.inDatagrams.onNext(datagram).onFailure {
        case t => t.printStackTrace()
      }(SameThreadExecutionContext)
      NoAction
    } else {
      val session = new CoapSession
      sessions.put(datagram.address, session)
      session.inDatagrams.onNext(datagram).onFailure {
        case t => t.printStackTrace()
      }(SameThreadExecutionContext)
      PushToBoth(new IncomingRequests(datagram.address, session.server), session.outDatagrams)
    }
  }

  /**
    * Clients can send requests and listen for responses.
    *
    */
  override def onOutputMessage(request: RequestExchange): ProcessingAction[IncomingRequests, Observable[Datagram]] = {
    val session = sessions.get(request.address)
    if (session != null) {
      session.client().onNext(request).onFailure {
        case t => t.printStackTrace()
      }(SameThreadExecutionContext)
      NoAction
    } else {
      val newSession = new CoapSession
      sessions.put(request.address, newSession)
      newSession.client().onNext(request).onFailure {
        case t => t.printStackTrace()
      }(SameThreadExecutionContext)
      PushToOutput(newSession.outDatagrams)
    }
  }
}
