package streams.io

import monifu.reactive.{Observer, Observable}

/**
  * Created by Paweł Sikora.
  */
object CoapStream {
  type CoapMsg = Any

  sealed trait Session {
    def incomingMessages: Observable[CoapMsg]

    def outgoingMessages: Observer[CoapMsg]

    def cancel(): Unit
  }

}
