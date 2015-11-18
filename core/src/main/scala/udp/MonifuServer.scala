package udp

import java.net.InetSocketAddress

import monifu.concurrent.Implicits.globalScheduler
import streams.AwaitableObserver
import streams.io.UdpStream
import streams.io.UdpStream.Datagram
import scala.concurrent.duration._
import scala.language.postfixOps

/**
  * Localhost: 380k req p second
  * Remote: 400k req p second
  */
object MonifuServer {

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
    val obs = new AwaitableObserver((d: Datagram) => {
      val ack = stream.onNext(d)
      stats.onSent()
      ack
    })
    stream.subscribe(obs)
    obs.await(1 day)
  }

}
