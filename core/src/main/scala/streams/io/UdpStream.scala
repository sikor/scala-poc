package streams.io

import java.net._
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.{LinkedBlockingQueue, TimeUnit}

import monifu.reactive.Ack.{Cancel, Continue}
import monifu.reactive.{Ack, Subscriber}
import org.slf4j.{Logger, LoggerFactory}
import streams.bidi.InOut
import streams.io.UdpStream.Datagram

import scala.annotation.tailrec
import scala.concurrent.duration._
import scala.concurrent.{Await, Future, TimeoutException}
import scala.language.postfixOps
import scala.util.control.NonFatal
import scala.util.{Failure, Success}


object UdpStream {

  val logger: Logger = LoggerFactory.getLogger(UdpStream.getClass)

  case class Datagram(data: ByteBuffer, address: InetSocketAddress)

}

/**
  * Created by Paweł Sikora.
  */
class UdpStream(val bindAddress: InetSocketAddress) extends InOut[Datagram, Datagram] {

  sealed trait WorkerState {
    def closed(): WorkerState = {
      this match {
        case Running => Finishing
        case _ => this
      }
    }
  }

  case object NotStarted extends WorkerState

  case object Running extends WorkerState

  case object Finishing extends WorkerState

  case object Finished extends WorkerState


  case class State(subscriber: Subscriber[Datagram], socket: DatagramSocket,
                   senderState: WorkerState, receiverState: WorkerState, wasBound: Boolean) {
    def senderShouldRun(): Boolean = {
      senderState == Running || (senderState == Finishing && !outgoingMessages.isEmpty)
    }

    def receiverShouldRun(): Boolean = {
      receiverState == Running
    }

    def workerUseSocket(workerState: WorkerState): Boolean = workerState == Running || workerState == Finishing

    def canCloseSocket: Boolean = !workerUseSocket(senderState) && !workerUseSocket(receiverState) && socket != null

  }

  private final val checkClosingIntervalMillis: Int = 2000
  private val stateRef: AtomicReference[State] = new AtomicReference[State](State(null, null, NotStarted, NotStarted, wasBound = false))
  private val outgoingMessages: LinkedBlockingQueue[Datagram] = new LinkedBlockingQueue[Datagram]()

  @tailrec
  final override def onSubscribe(subscriber: Subscriber[Datagram]): Unit = {
    val seenState = stateRef.get()
    if (seenState.subscriber != null) {
      subscriber.onError(new IllegalArgumentException("Maximally one subscriber is allowed."))
    } else if (seenState.socket == null) {
      subscriber.onError(new IllegalArgumentException("Socket is closed"))
    } else {
      if (!compareAndSet(seenState, seenState.copy(subscriber = subscriber))) {
        onSubscribe(subscriber)
      } else {
        try {
          modState(stateRef.get(), s => s.copy(receiverState = Running))
          val receiverThread: Thread = new Thread(Receiver)
          receiverThread.start()
        } catch {
          case NonFatal(e) => subscriber.onError(e)
        }
      }
    }
  }

  @tailrec
  final def bind(): Unit = {
    val seenState: State = stateRef.get()
    if (seenState.wasBound) {
      throw new IllegalArgumentException("socket was already bound")
    }
    if (!compareAndSet(seenState, seenState.copy(wasBound = true))) {
      bind()
    } else {
      val serverSocket: DatagramSocket = new DatagramSocket(bindAddress)
      serverSocket.setSendBufferSize(66000 * 100)
      serverSocket.setReceiveBufferSize(66000 * 100)
      serverSocket.setSoTimeout(checkClosingIntervalMillis)
      modState(seenState, s => s.copy(socket = serverSocket))
    }
  }

  /**
    * If there is some worker than change its state to finishing and let him close the socket after work.
    * Otherwise close the socket directly.
    */
  def close(): Unit = {
    val seenState: State = stateRef.get()
    val state = modState(seenState, s => s.copy(senderState = s.senderState.closed(), receiverState = s.receiverState.closed()))
    tryCloseSocket(state)
  }


  object Receiver extends Runnable {

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
            case Cancel => false
          }
          case Failure(t) => stateRef.get().subscriber.scheduler.reportFailure(t)
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
      } finally {
        modState(stateRef.get(), s => s.copy(receiverState = Finished))
        tryCloseSocket(stateRef.get())
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
        modState(stateRef.get(), s => s.copy(senderState = Finished))
        tryCloseSocket(stateRef.get())
      }
    }
  }

  @tailrec
  private def tryCloseSocket(state: State): Unit = {
    if (state.canCloseSocket) {
      if (compareAndSet(state, state.copy(socket = null))) {
        state.socket.close()
      } else {
        tryCloseSocket(stateRef.get())
      }
    }
  }

  @tailrec
  final override def onNext(elem: Datagram): Future[Ack] = {
    val state = stateRef.get()
    if (state.socket == null) {
      Future.failed(new IllegalStateException("Got onNext but socket is closed."))
    }
    state.senderState match {
      case Running => outgoingMessages.add(elem)
        Continue
      case NotStarted =>
        if (!compareAndSet(state, state.copy(senderState = Running))) {
          onNext(elem)
        } else {
          val senderThread: Thread = new Thread(Sender)
          senderThread.start()
          outgoingMessages.add(elem)
          Continue
        }
      case Finishing | Finished => Future.failed(new IllegalStateException("Got onNext after stream was closed."))
    }
  }

  override def onError(ex: Throwable): Unit = {
    UdpStream.logger.error("Received error in udp datagram sender", ex)
    modState(stateRef.get(), s => s.copy(senderState = s.senderState.closed()))
  }

  override def onComplete(): Unit = {
    modState(stateRef.get(), s => s.copy(senderState = s.senderState.closed()))
  }

  private def compareAndSet(expectedState: State, newState: State): Boolean = {
    val curState: State = stateRef.get()
    (curState eq expectedState) && stateRef.compareAndSet(expectedState, newState)
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
}
