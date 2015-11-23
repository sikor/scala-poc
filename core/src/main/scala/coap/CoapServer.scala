package coap

import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.util.concurrent.{Executors, ThreadFactory}

import coap.core.message._
import coap.core.{CoapEnvelope, IncomingMessageEnvelope, NonListenableMessageEnvelope}
import monifu.concurrent.schedulers.AsyncScheduler
import monifu.concurrent.{Scheduler, UncaughtExceptionReporter}
import streams.Udp
import streams.Udp.Datagram
import udp.Statistics

import scala.concurrent.ExecutionContext
import scala.language.postfixOps

/**
  * 80k req p seconds localhost
  */
object CoapServer {

  implicit val globalScheduler: Scheduler =
    AsyncScheduler(
      Executors.newSingleThreadScheduledExecutor(new ThreadFactory {
        def newThread(r: Runnable): Thread = {
          val th = new Thread(r)
          th.setDaemon(true)
          th.setName("benchmark-scheduler")
          th
        }
      }),
      ExecutionContext.fromExecutor(Executors.newFixedThreadPool(1, new ThreadFactory {
        override def newThread(r: Runnable): Thread = {
          val th = new Thread(r)
          th.setDaemon(true)
          th.setName("benchmark-executor")
          th
        }
      })),
      UncaughtExceptionReporter.LogExceptionsToStandardErr
    )

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
      stats.onSent()
      NonListenableMessageEnvelope(
        CoapMessage(Acknowledgement, Content, in.message.messageId, in.message.token, Options.empty, payload),
        in.address)
    })
  }

}
