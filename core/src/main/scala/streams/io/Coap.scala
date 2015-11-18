package streams.io

import java.net.InetSocketAddress

import monifu.reactive.{Observable, Observer}
import streams.io.Coap.Session

/**
  * Created by Paweł Sikora.
  */
object Coap {
  type CoapMsg = Any

  sealed trait Session {
    def incomingMessages: Observable[CoapMsg]

    def outgoingMessages: Observer[CoapMsg]

    def cancel(): Unit
  }

}

trait Coap {

  def sessions(): Observable[Session]

  def getOrCreateSession(address: InetSocketAddress): Session

}
