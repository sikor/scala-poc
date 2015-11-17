package streams.io

import java.net.{InetSocketAddress, SocketAddress}
import java.nio.ByteBuffer
import java.nio.channels.{SelectionKey, Selector, DatagramChannel}
import java.util.concurrent.atomic.AtomicReference

import monifu.reactive.{Observable, Subscriber}
import streams.io.UdpStream.Datagram

import scala.annotation.tailrec


object UdpStream {

  case class Datagram(data: ByteBuffer, address: SocketAddress)

}

/**
  * Created by Paweł Sikora.
  */
class UdpStream(val bindAddress: InetSocketAddress, val maxReceiveSize: Int = 2048) extends Observable[Datagram] {

  private val stateRef: AtomicReference[State] = new AtomicReference[State](State(null, null))

  @tailrec
  final override def onSubscribe(subscriber: Subscriber[Datagram]): Unit = {
    val seenState = stateRef.get()
    if (seenState.subscriber != null) {
      subscriber.onError(new IllegalArgumentException("Maximally one subscriber is allowed."))
    } else {
      if (!compareAndSet(seenState, seenState.copy(subscriber = subscriber))) {
        onSubscribe(subscriber)
      } else {
        subscriber.scheduler.execute(new Runnable {
          override def run(): Unit = bind()
        })
      }
    }
  }

  private def bind(): Unit = {
    val channel: DatagramChannel = DatagramChannel.open
    channel.bind(bindAddress)
    channel.configureBlocking(false)
    val selector: Selector = Selector.open
    val selectionKey = channel.register(selector, SelectionKey.OP_READ)
    modState(stateRef.get(), s => s.copy(selectionKey = selectionKey))

  }

  private def startReceiving(): Unit = {

  }


  private def compareAndSet(expectedState: State, newState: State): Boolean = {
    val curState: State = stateRef.get()
    (curState eq expectedState) && stateRef.compareAndSet(expectedState, newState)
  }

  case class State(subscriber: Subscriber[Datagram], selectionKey: SelectionKey)

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


}
