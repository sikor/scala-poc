package streams.io

import java.net.InetSocketAddress

import monifu.reactive.{Observable, Observer}
import streams.io.CoapSessions.CoapSession

/**
  * Created by Paweł Sikora.
  */
object CoapSessions {
  type CoapMsg = Any

  sealed trait CoapSession {
    def incomingMessages: Observable[CoapMsg]

    def outgoingMessages: Observer[CoapMsg]

    def cancel(): Unit
  }

}

trait CoapSessions {

  def sessions(): Observable[CoapSession]

  def getOrCreateSession(address: InetSocketAddress): CoapSession

}
