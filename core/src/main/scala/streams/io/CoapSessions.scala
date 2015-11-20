package streams.io

import java.net.InetSocketAddress
import java.util

import _root_.streams.io.CoapSessions.{IncomingRequests, CoapSession, Request}
import monifu.reactive.observers.SynchronousObserver
import streams.bidi.BidiProcessor
import streams.bidi.BidiStream.{NoAction, ProcessingAction, PushToBoth, PushToOutput}
import streams.io.UdpStream.Datagram
import monifu.reactive._


/**
  * Created by Paweł Sikora.
  */
object CoapSessions {
  type CoapMsg = Any


  /**
    *
    * When this session is closed it won't produce any new messages. It won't accept any new datagrams.
    * So when it signalize onComplete than subscriber can be released. New session for this address will be created.
    * It takes the stream of clients. Each has associated token for message filtering. Block-wise handling is invisible
    * for client. Server for all requests can be registered. Observation Responses without associated client can be
    * accessed separately.
    *
    * @param address associated target address
    */
  class CoapSession(val address: InetSocketAddress) {

    def server(): Observable[Request] = ???


    def client(): SynchronousObserver[Request] = ???

    /**
      * @return Responses for requests, and requests from clients.
      */
    def outDatagrams: Observable[Datagram] = ???

    /**
      * Each session has own back-pressure for incoming datagrams.
      */
    def inDatagrams: Channel[Datagram] = ???

  }

  class IncomingRequests(val address: InetSocketAddress, requests: Observable[Request])

  class Request(val address: InetSocketAddress, val request: CoapMsg, val responses: Observer[CoapMsg])

}

class CoapSessions extends BidiProcessor[Datagram, IncomingRequests, Request, Observable[Datagram]] {

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
      oldSession.inDatagrams.pushNext(datagram)
      NoAction
    } else {
      val session = new CoapSession(datagram.address)
      sessions.put(datagram.address, session)
      session.inDatagrams.pushNext(datagram)
      PushToBoth(new IncomingRequests(datagram.address, session.server()), session.outDatagrams)
    }
  }

  /**
    * Clients can send requests and listen for responses.
    *
    */
  override def onOutputMessage(request: Request): ProcessingAction[IncomingRequests, Observable[Datagram]] = {
    val session = sessions.get(request.address)
    if (session != null) {
      session.client().onNext(request)
      NoAction
    } else {
      val newSession = new CoapSession(request.address)
      sessions.put(request.address, newSession)
      newSession.client().onNext(request)
      PushToOutput(newSession.outDatagrams)
    }
  }
}
