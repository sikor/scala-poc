package streams.io

import java.net.{DatagramPacket, DatagramSocket, InetSocketAddress, SocketAddress}
import java.nio.ByteBuffer
import java.nio.channels.SelectionKey
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicReference

import monifu.reactive.{Observable, Subscriber}
import streams.io.UdpStream.Datagram

import scala.annotation.tailrec
import scala.util.control.NonFatal


object UdpStream {

  case class Datagram(data: ByteBuffer, address: SocketAddress)

}

/**
  * Created by Paweł Sikora.
  */
class UdpStream(val bindAddress: InetSocketAddress) extends Observable[Datagram] {

  private val stateRef: AtomicReference[State] = new AtomicReference[State](State(null, null))
  private val outgoingMessages: LinkedBlockingQueue[Datagram] = new LinkedBlockingQueue[Datagram](2048)

  @tailrec
  final override def onSubscribe(subscriber: Subscriber[Datagram]): Unit = {
    val seenState = stateRef.get()
    if (seenState.subscriber != null) {
      subscriber.onError(new IllegalArgumentException("Maximally one subscriber is allowed."))
    } else {
      if (!compareAndSet(seenState, seenState.copy(subscriber = subscriber))) {
        onSubscribe(subscriber)
      } else {
        bind()
      }
    }
  }

  private def bind(): Unit = {
    val serverSocket: DatagramSocket = new DatagramSocket(bindAddress)
    serverSocket.setSendBufferSize(66000 * 100)
    serverSocket.setReceiveBufferSize(66000 * 100)
    val senderThread: Thread = new Thread(sender)
    val receiverThread: Thread = new Thread(receiver)
    senderThread.start()
    receiverThread.start()
  }

  private def compareAndSet(expectedState: State, newState: State): Boolean = {
    val curState: State = stateRef.get()
    (curState eq expectedState) && stateRef.compareAndSet(expectedState, newState)
  }

  case class State(subscriber: Subscriber[Datagram], socket: DatagramSocket)

  case class StateMod(mod: State => State)

  @tailrec
  private def modState(state: State, mod: State => State): State = {
    val newState: State = mod(state)
    if (!compareAndSet(state, newState)) {
      modState(stateRef.get(), mod)
    } else {
      newState
    }
  }


  val receiver: Runnable = new Runnable() {
    def run() = {
      val receiveData: Array[Byte] = Array.ofDim(65000)
      val socket: DatagramSocket = stateRef.get().socket
      val subscriber: Subscriber[Datagram] = stateRef.get().subscriber
      try {
        while (true) {
          val receivePacket = new DatagramPacket(receiveData, receiveData.length)
          socket.receive(receivePacket)
        }
      } catch {
        case NonFatal(e) => subscriber.onError(e)
      }
    }
  }


  val sender: Runnable = new Runnable() {
    def run() = {
      val socket: DatagramSocket = stateRef.get().socket
      try {
        while (true) {
          val msg = outgoingMessages.take()
          val packet: DatagramPacket = new DatagramPacket(msg.data.array(), msg.data.limit(), msg.address)
          socket.send(packet)
        }
      } catch {
        case NonFatal(e) => stateRef.get().subscriber.onError(e)
      }
    }
  }


}
