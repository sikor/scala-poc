package coap

import java.net.{InetAddress, InetSocketAddress}
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.util.concurrent.{Executors, ThreadFactory}

import coap.core._
import coap.core.message._
import monifu.concurrent.schedulers.AsyncScheduler
import monifu.concurrent.{Scheduler, UncaughtExceptionReporter}
import monifu.reactive.{Ack, Observable, Observer}
import streams.Udp.Datagram
import streams.{SameThreadExecutionContext, Udp}
import udp.Statistics

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.language.postfixOps
import scala.util.{Failure, Success}


object CoapClient {

  val globalScheduler: Scheduler =
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
    var targetAddress: InetSocketAddress = null
    if (args.length == 2) {
      targetAddress = new InetSocketAddress(args(0), Integer.valueOf(args(1)))
    } else {
      targetAddress = new InetSocketAddress(InetAddress.getByName("localhost"), 9876)
    }
    val coapProcessor = new CoapMessageProcessor[Any]()
    val payload: Payload = Payload("hi!".getBytes(Charset.forName("UTF-8")))
    def msg() = CoapMessage(Confirmable, Get, coapProcessor.generateMid(targetAddress),
      coapProcessor.generateTid(targetAddress), Options.empty, payload)
    Udp(new InetSocketAddress(0)).map(
      d => IncomingMessageEnvelope(MessageParser.parse(d.data.array()), d.address),
      (e: CoapEnvelope) => Datagram(ByteBuffer.wrap(MessageSerializer.serialize(e.message)), e.address))
      .coapMatcher.write(Observable.create { subscriber =>
      def send(): Unit = {
        val ansPromise = Promise[IncomingMessageEnvelope]
        val observer = new Observer[IncomingMessageEnvelope] {
          override def onError(ex: Throwable): Unit = ex.printStackTrace()

          override def onComplete(): Unit = {}

          override def onNext(elem: IncomingMessageEnvelope): Future[Ack] = {
            stats.onSent()
            ansPromise.success(elem)
            Ack.Continue
          }
        }
        val ack = subscriber.onNext(ListenableEnvelope(msg(), targetAddress, observer))
        ack.flatMap {
          case Ack.Continue => ansPromise.future
          case _ => throw new IllegalArgumentException
        }(SameThreadExecutionContext).onComplete {
          case Success(ans) => send()
          case Failure(ex) => ex.printStackTrace()
        }(SameThreadExecutionContext)
      }
      send()
    })(globalScheduler)

  }

}
