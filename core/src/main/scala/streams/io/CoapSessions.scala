package streams.io

import java.net.InetSocketAddress
import java.util

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


  class CoapSession(val address: InetSocketAddress, val initMsg: CoapMsg) {

    def stream(): InOut[CoapMsg, CoapMsg] = ???

    def outDatagrams: Observable[Datagram] = ???

    def inDatagrams: Channel[Datagram] = ???


  }

  class CoapClient(val stream: InOut[CoapMsg, CoapMsg], val targetAddress: InetSocketAddress, val initMsg: CoapMsg) {

  }

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
      client.stream.onError(new IllegalStateException("Session with this address already exists: " + client.targetAddress))
      NoAction
    } else {
      val session = new CoapSession(client.targetAddress, client.initMsg)
      sessions.put(client.targetAddress, session)
      session.stream().subscribe(client.stream)
      client.stream.subscribe(session.stream())
      PushToOutput(session.outDatagrams)
    }
  }
}
