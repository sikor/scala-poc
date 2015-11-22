package streams.coap.io

import java.net._
import java.nio.ByteBuffer
import java.util.concurrent.{LinkedBlockingQueue, TimeUnit}

import monifu.concurrent.atomic.Atomic
import monifu.reactive.Ack.{Cancel, Continue}
import monifu.reactive.{Ack, Observer}
import streams.SameThreadExecutionContext
import streams.bidi.Bidirectional

import scala.concurrent.duration._
import scala.concurrent.{Await, Future, Promise}
import scala.language.postfixOps
import scala.util.control.NonFatal
import scala.util.{Failure, Success}


object Udp {

  case class Datagram(data: ByteBuffer, address: InetSocketAddress)

  private val checkClosingIntervalMillis: Int = 2000

  def apply(bindAddress: InetSocketAddress): Bidirectional[Datagram, Datagram] = {
    Bidirectional.create[Datagram, Datagram] { (subscription) =>
      val socket: DatagramSocket = new DatagramSocket(bindAddress)
      val receiveData: Array[Byte] = Array.ofDim(socket.getReceiveBufferSize)
      val packet = new DatagramPacket(receiveData, receiveData.length)

      // start with sending to get subscriber.
      val continueWhenQueueIsEmpty = Atomic(true)
      val outgoingMessages = new LinkedBlockingQueue[Datagram]()
      val sender = new StopableWorker(() => {
        val msg = outgoingMessages.poll(checkClosingIntervalMillis, TimeUnit.MILLISECONDS)
        if (msg != null) {
          val packet = new DatagramPacket(msg.data.array(), msg.data.limit(), msg.address)
          socket.send(packet)
          true
        } else {
          continueWhenQueueIsEmpty.get
        }
      })
      val senderThread = new Thread(sender)
      senderThread.start()
      val subscriber = subscription.connect(new Observer[Datagram] {
        override def onNext(elem: Datagram): Future[Ack] = {
          outgoingMessages.add(elem)
          Continue
        }

        override def onError(ex: Throwable): Unit = {
          continueWhenQueueIsEmpty.set(false)
          subscription.scheduler.reportFailure(ex)
        }

        override def onComplete(): Unit = {
          continueWhenQueueIsEmpty.set(false)
        }
      })

      //now when we have subscriber we can start receiving
      val receiver = new StopableWorker(() => {
        socket.receive(packet)
        val data = ByteBuffer.allocate(packet.getLength)
        data.put(packet.getData, 0, packet.getLength)
        val datagram = Datagram(data, new InetSocketAddress(packet.getAddress, packet.getPort))
        val subAck = waitForAck(subscriber.onNext(datagram))
        if (subAck == Continue) {
          true
        } else {
          subAck.value.get match {
            case Success(a) => a match {
              case Continue => true
              case Cancel => false
            }
            case Failure(t) => subscription.scheduler.reportFailure(t)
              false
          }
        }
      })
      val receiverThread = new Thread(receiver)
      receiverThread.start()


      // close socket after subscriber and observable are finished
      implicit val ec = SameThreadExecutionContext
      sender.whenTerminated.onComplete(
        _ => receiver.whenTerminated.onComplete(_ => socket.close())
      )
      sender.whenTerminated.onFailure {
        case NonFatal(e) => subscription.scheduler.reportFailure(e)
      }
      receiver.whenTerminated.onFailure {
        case NonFatal(e) => subscription.scheduler.reportFailure(e)
      }
    }
  }

  private def waitForAck(subAck: Future[Ack]): Future[Ack] = {
    if (subAck != Continue && !subAck.isCompleted) {
      Await.result(subAck, Duration.Inf)
    } else {
      subAck
    }
  }

  private class StopableWorker(runLoop: () => Boolean) extends Runnable {
    @volatile
    private var shouldRun: Boolean = true
    private val terminationPromise = Promise[Unit]

    def stop(): Future[Unit] = {
      shouldRun = false
      terminationPromise.future
    }

    def whenTerminated: Future[Unit] = terminationPromise.future

    final def run(): Unit = {
      while (shouldRun) {
        try {
          if (!runLoop()) {
            shouldRun = false
          }
        } catch {
          case NonFatal(e) => terminationPromise.failure(e)
            shouldRun = false
        }
      }
      if (!terminationPromise.isCompleted) {
        terminationPromise.success(())
      }
    }
  }

}