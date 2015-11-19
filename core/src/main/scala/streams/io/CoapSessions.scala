package streams.io

import java.net.InetSocketAddress
import java.util

import monifu.reactive.{Subscriber, Ack, Observable}
import streams.bidi.BidiStream.ProcessingAction
import streams.bidi.{BidiProcessor, InOut}
import streams.io.CoapSessions.{CoapClient, CoapSession}
import streams.io.UdpStream.Datagram

import scala.concurrent.Future


/**
  * Created by Paweł Sikora.
  */
object CoapSessions {
  type CoapMsg = Any


  class CoapSession(val address: InetSocketAddress) extends InOut[CoapMsg, CoapMsg] {
    override def onSubscribe(subscriber: Subscriber[CoapMsg]): Unit = ???

    override def onError(ex: Throwable): Unit = ???

    override def onComplete(): Unit = ???

    override def onNext(elem: CoapMsg): Future[Ack] = ???
  }

  class CoapClient(val stream: InOut[CoapMsg, CoapMsg], val address: InetSocketAddress)

}

class CoapSessions extends BidiProcessor[Datagram, CoapSession, CoapClient, Observable[Datagram]] {

  private val sessions = new util.HashMap[InetSocketAddress, CoapSession]

  override def onInputMessage(inputMsg: Datagram): ProcessingAction[CoapSession, Observable[Datagram]] = ???

  override def onOutputMessage(outputMsg: CoapClient): ProcessingAction[CoapSession, Observable[Datagram]] = ???
}
