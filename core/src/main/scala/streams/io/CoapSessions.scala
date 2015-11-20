package streams.io

import java.net.InetSocketAddress
import java.util

import monifu.concurrent.Scheduler
import monifu.reactive.observers.SynchronousObserver
import streams.bidi.BidiStream.{PushToOutput, NoAction, ProcessingAction, PushToBoth}
import streams.bidi.{BidiProcessor, InOut}
import streams.io.CoapSessions.{CoapClient, CoapSession}
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
    * @param initMsg message which initiated the session.
    */
  class CoapSession(val address: InetSocketAddress, val initMsg: CoapMsg) {

    def requests(): Observable[InOut[CoapMsg, CoapMsg]] = ???

    def clients(): SynchronousObserver[InOut[CoapMsg, CoapMsg]] = ???

    /**
      * @return Responses for requests, and requests from clients.
      */
    def outDatagrams: Observable[Datagram] = ???

    /**
      * Each session has own back-pressure for incoming datagrams.
      */
    def inDatagrams: Channel[Datagram] = ???

  }

  class CoapClient(val stream: InOut[CoapMsg, CoapMsg], val targetAddress: InetSocketAddress, val initMsg: CoapMsg)

}

class CoapSessions extends BidiProcessor[Datagram, CoapSession, CoapClient, Observable[Datagram]] {

  private val sessions = new util.HashMap[InetSocketAddress, CoapSession]

  override def onInputMessage(datagram: Datagram): ProcessingAction[CoapSession, Observable[Datagram]] = {
    val oldSession = sessions.get(datagram.address)
    if (oldSession != null) {
      oldSession.inDatagrams.pushNext(datagram)
      NoAction
    } else {
      val session = new CoapSession(datagram.address, datagram)
      sessions.put(datagram.address, session)
      PushToBoth(session, session.outDatagrams)
    }
  }

  override def onOutputMessage(client: CoapClient): ProcessingAction[CoapSession, Observable[Datagram]] = {
    val oldSession = sessions.get(client.targetAddress)
    if (oldSession != null) {
      oldSession.clients().onNext(client.stream)
      NoAction
    } else {
      val session = new CoapSession(client.targetAddress, client.initMsg)
      sessions.put(client.targetAddress, session)
      session.clients().onNext(client.stream)
      PushToOutput(session.outDatagrams)
    }
  }
}
