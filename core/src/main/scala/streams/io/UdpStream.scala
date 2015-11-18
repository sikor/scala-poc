package streams.io

import java.net._
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.{LinkedBlockingQueue, TimeUnit}

import monifu.reactive.Ack.{Cancel, Continue}
import monifu.reactive.{Observer, Ack, Observable, Subscriber}
import org.slf4j.{LoggerFactory, Logger}
import streams.io.UdpStream.Datagram

import scala.annotation.tailrec
import scala.concurrent.duration._
import scala.concurrent.{TimeoutException, Await, Future}
import scala.language.postfixOps
import scala.util.control.NonFatal
import scala.util.{Failure, Success}


object UdpStream {

  val logger: Logger = LoggerFactory.getLogger(UdpStream.getClass)

  case class Datagram(data: ByteBuffer, address: SocketAddress)

}

/**
  * Created by Paweł Sikora.
  */
class UdpStream(val bindAddress: InetSocketAddress) extends Observable[Datagram] with Observer[Datagram] {

  sealed trait SocketState

  case object NotBound extends SocketState

  case object Bound extends SocketState

  case object Closing extends SocketState

  case object Closed extends SocketState

  sealed trait WorkerState

  case object NotStarted extends WorkerState

  case object Running extends WorkerState

  case object Finishing extends WorkerState

  case object Finished extends WorkerState


  case class State(subscriber: Subscriber[Datagram], socket: DatagramSocket, socketState: SocketState,
                   stopSender: Boolean, stopReceiver: Boolean) {
    def senderShouldRun(): Boolean = {
      !stopSender && socketState == Bound
    }

    def receiverShouldRun(): Boolean = {
      !stopReceiver && socketState == Bound
    }

    def canCloseSocket: Boolean = {
      stopSender && stopReceiver && socketState == Bound
    }
  }

  private final val checkClosingIntervalMillis: Int = 2000
  private val stateRef: AtomicReference[State] = new AtomicReference[State](State(null, null, NotBound,
    stopSender = false, stopReceiver = false))
  private val outgoingMessages: LinkedBlockingQueue[Datagram] = new LinkedBlockingQueue[Datagram]()

  @tailrec
  final override def onSubscribe(subscriber: Subscriber[Datagram]): Unit = {
    val seenState = stateRef.get()
    if (seenState.subscriber != null) {
      subscriber.onError(new IllegalArgumentException("Maximally one subscriber is allowed."))
    } else {
      if (!compareAndSet(seenState, seenState.copy(subscriber = subscriber))) {
        onSubscribe(subscriber)
      } else {
        try {
          bind()
        } catch {
          case NonFatal(e) => subscriber.onError(e)
        }
      }
    }
  }

  private def bind(): Unit = {
    val serverSocket: DatagramSocket = new DatagramSocket(bindAddress)
    serverSocket.setSendBufferSize(66000 * 100)
    serverSocket.setReceiveBufferSize(66000 * 100)
    serverSocket.setSoTimeout(checkClosingIntervalMillis)
    modState(stateRef.get(), s => s.copy(socket = serverSocket, socketState = Bound))
    val senderThread: Thread = new Thread(Sender)
    val receiverThread: Thread = new Thread(Receiver)
    senderThread.start()
    receiverThread.start()
  }

  @tailrec
  private def canReceiveNextPacket(subAck: Future[Ack]): Boolean = {
    if (!stateRef.get().receiverShouldRun()) {
      false
    } else if (subAck == Continue) {
      true
    } else if (subAck.isCompleted) {
      subAck.value.get match {
        case Success(a) => a match {
          case Continue => true
          case Cancel => close()
            false
        }
        case Failure(t) => stateRef.get().subscriber.scheduler.reportFailure(t)
          close()
          false
      }
    } else {
      val result = try {
        Await.result(subAck, checkClosingIntervalMillis millis)
      } catch {
        case _: TimeoutException => subAck
      }
      canReceiveNextPacket(result)
    }
  }

  private def close(): Unit = {
    modState(stateRef.get(), s => s.copy(socketState = Closing))
  }

  object Receiver extends Runnable {

    private def pushToSubscriber(subscriber: Subscriber[Datagram], receivePacket: DatagramPacket): Future[Ack] = {
      val data = ByteBuffer.allocate(receivePacket.getLength)
      data.put(receivePacket.getData, 0, receivePacket.getLength)
      val datagram = Datagram(data, new InetSocketAddress(receivePacket.getAddress, receivePacket.getPort))
      subscriber.onNext(datagram)
    }

    private def receivePacket(receiveData: Array[Byte], socket: DatagramSocket): DatagramPacket = {
      val receivePacket = new DatagramPacket(receiveData, receiveData.length)
      socket.receive(receivePacket)
      receivePacket
    }

    def run() = {
      val subscriber: Subscriber[Datagram] = stateRef.get().subscriber
      val receiveData: Array[Byte] = Array.ofDim(65000)
      val socket: DatagramSocket = stateRef.get().socket
      var subAck: Future[Ack] = Continue
      try {
        while (canReceiveNextPacket(subAck)) {
          try {
            val receivedPacket = receivePacket(receiveData, socket)
            subAck = pushToSubscriber(subscriber, receivedPacket)
          } catch {
            case _: SocketTimeoutException => //wake up every checkClosingIntervalMillis to check is stream is closing
            case NonFatal(e) => subscriber.onError(e)
          }
        }
      } catch {
        case NonFatal(e) => subscriber.scheduler.reportFailure(e)
      }
    }
  }


  object Sender extends Runnable {
    def run() = {
      try {
        val socket: DatagramSocket = stateRef.get().socket
        var state = stateRef.get()
        while (state.senderShouldRun()) {
          //wake up every checkClosingIntervalMillis to check if stream is closing
          val msg = outgoingMessages.poll(checkClosingIntervalMillis, TimeUnit.MILLISECONDS)
          if (msg != null) {
            val packet: DatagramPacket = new DatagramPacket(msg.data.array(), msg.data.limit(), msg.address)
            socket.send(packet)
          }
          state = stateRef.get()
        }
      } catch {
        case NonFatal(e) => stateRef.get().subscriber.scheduler.reportFailure(e)
      } finally {
        tryCloseSocket(stateRef.get())
      }
    }
  }

  @tailrec
  private def tryCloseSocket(state: State): Unit = {
    if (state.canCloseSocket) {
      if (compareAndSet(state, state.copy(socketState = Closed))) {
        state.socket.close()
      } else {
        tryCloseSocket(stateRef.get())
      }
    }
  }

  @tailrec
  private def modState(state: State, mod: State => State): State = {
    val newState: State = mod(state)
    if (!compareAndSet(state, newState)) {
      modState(stateRef.get(), mod)
    } else {
      newState
    }
  }


  private def compareAndSet(expectedState: State, newState: State): Boolean = {
    val curState: State = stateRef.get()
    (curState eq expectedState) && stateRef.compareAndSet(expectedState, newState)
  }

  override def onNext(elem: Datagram): Future[Ack] = {
    outgoingMessages.add(elem)
    Continue
  }

  override def onError(ex: Throwable): Unit = {
    UdpStream.logger.error("Received error in udp datagram sender", ex)
    modState(stateRef.get(), s => s.copy(stopSender = true))
  }

  override def onComplete(): Unit = {
    modState(stateRef.get(), s => s.copy(stopSender = true))
  }
}
