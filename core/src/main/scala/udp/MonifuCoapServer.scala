package udp

import java.net.InetSocketAddress

import monifu.concurrent.Implicits.globalScheduler
import monifu.reactive.OverflowStrategy.BackPressure
import streams.AwaitableObserver
import streams.coap.core.message.{Content, MessageId, Options, Payload}
import streams.coap.core.{IncomingMessageEnvelope, ResponseBuilder}
import streams.coap.io.CoapSessions.RequestExchange
import streams.coap.io.{CoapSessions, UdpStream}

import scala.concurrent.duration._
import scala.language.postfixOps

object MonifuCoapServer {

  private val stats: Statistics = new Statistics

  def main(args: Array[String]): Unit = {
    var bindAddr: InetSocketAddress = null
    if (args.length == 2) {
      bindAddr = new InetSocketAddress(args(0), Integer.valueOf(args(1)))
    } else {
      bindAddr = new InetSocketAddress(9876)
    }
    val stream = new UdpStream(bindAddr)
    stream.bind()

    val obs = new AwaitableObserver((request: RequestExchange) => {
      println("got msg!!!")
      val response = ResponseBuilder(IncomingMessageEnvelope(request.request, request.address))(_ => MessageId(0))
        .buildResponse(Content, Options.empty, Payload("THX!".getBytes("UTF-8")))
      stats.onSent()
      request.responses.onNext(response)
    })
    stream.mergeOut(BackPressure(2048)).mapInOut(new CoapSessions).map(requests => requests.requests).merge.subscribe(obs)
    obs.await(1 day)
  }

}
