package udp

import java.net.{InetAddress, InetSocketAddress}

import monifu.concurrent.Implicits.globalScheduler
import monifu.reactive.OverflowStrategy.BackPressure
import streams.AwaitableObserver
import streams.bidi.InOut
import streams.coap.core.CoapMessageProcessor
import streams.coap.core.message._
import streams.coap.io.CoapSessions.{IncomingRequests, CoapMsg, RequestExchange}
import streams.coap.io.{CoapSessions, UdpStream}

import scala.language.postfixOps

object MonifuCoapClient {

  class ResendObserver(start: Long, time: Long, targetAddr: InetSocketAddress, genMsg: () => CoapMsg, coapStream: InOut[IncomingRequests, RequestExchange])
    extends AwaitableObserver[CoapMsg](_ => {
      if ((System.currentTimeMillis - start) / 1000 < time) {
        coapStream.onNext(new RequestExchange(targetAddr, genMsg(), new ResendObserver(start, time, targetAddr, genMsg, coapStream)))
      } else {
        monifu.reactive.Ack.Continue
      }
    })

  def main(args: Array[String]): Unit = {
    var targetAddr: InetSocketAddress = null
    if (args.length == 2) {
      targetAddr = new InetSocketAddress(InetAddress.getByName(args(0)), Integer.valueOf(args(1)))
    } else {
      targetAddr = new InetSocketAddress(InetAddress.getByName("localhost"), 9876)
    }
    System.out.println("send Address: " + targetAddr)
    val udp = new UdpStream(new InetSocketAddress(0))
    udp.bind()

    val msgProc = new CoapMessageProcessor[Any]()
    def genMsg() = {
      CoapMessage(NonConfirmable, Get, msgProc.generateMid(targetAddr), msgProc.generateTid(targetAddr),
        Options.empty, Payload(s"Hi!".getBytes("UTF-8")))
    }
    val start: Long = System.currentTimeMillis
    val time: Long = 100
    val coapStream = udp.mergeOut(BackPressure(2048)).mapInOut(new CoapSessions)
    val firstMsg = new RequestExchange(targetAddr, genMsg(), new ResendObserver(start, time, targetAddr, genMsg, coapStream))
    coapStream.onNext(firstMsg)
  }

}
