package udp

import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.charset.Charset

import monifu.concurrent.Implicits.globalScheduler
import streams.coap.core.message._
import streams.coap.core.{CoapEnvelope, IncomingMessageEnvelope, NonListenableMessageEnvelope}
import streams.coap.io.Udp
import streams.coap.io.Udp.Datagram

import scala.language.postfixOps

/**
  * Localhost: 380k req p second
  * Remote: 400k req p second
  */
object CoapClient {

  private val stats: Statistics = new Statistics

  def main(args: Array[String]): Unit = {
    var bindAddr: InetSocketAddress = null
    if (args.length == 2) {
      bindAddr = new InetSocketAddress(args(0), Integer.valueOf(args(1)))
    } else {
      bindAddr = new InetSocketAddress(9876)
    }
    val payload: Payload = Payload("hi!".getBytes(Charset.forName("UTF-8")))
    Udp(bindAddr)
      .map(
        d => IncomingMessageEnvelope(MessageParser.parse(d.data.array()), d.address),
        (e: CoapEnvelope) => Datagram(ByteBuffer.wrap(MessageSerializer.serialize(e.message)), e.address))
      .coapMatcher.shortCircuit(in => {
      NonListenableMessageEnvelope(
        CoapMessage(Acknowledgement, Content, in.message.messageId, in.message.token, Options.empty, payload),
        in.address)
    })
  }

}
