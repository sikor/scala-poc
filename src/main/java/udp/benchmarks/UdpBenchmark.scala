package udp.benchmarks

import java.net.{DatagramPacket, DatagramSocket, InetAddress, InetSocketAddress}
import java.nio.ByteBuffer
import java.nio.channels.{SelectionKey, Selector, DatagramChannel}

import org.openjdk.jmh.annotations._

/**
  * Created by Paweł Sikora.
  */

object UdpBenchmark {

  @State(Scope.Thread)
  class ThreadState {
    var counter = 0

    @TearDown(Level.Iteration)
    def tearDown(): Unit = {
      counter = 0
    }
  }

}

@State(Scope.Benchmark)
class UdpBenchmark {


  var socket: DatagramSocket = _
  var sendPacket: DatagramPacket = _
  var sendLocalHost: DatagramPacket = _
  val ipAddress: InetAddress = InetAddress.getByName("192.169.1.106")
  val localhost: InetAddress = InetAddress.getByName("localhost")
  val port = 12123
  var channel: DatagramChannel = _
  var nioByteBuffer: ByteBuffer = _
  var targetAddress: InetSocketAddress = _
  var localhostTarget: InetSocketAddress = _

  var selector: Selector = _

  @Setup(Level.Trial)
  def setupBench(): Unit = {
    socket = new DatagramSocket(9876)
    socket.setSendBufferSize(65000 * 5)
    val sendData: Array[Byte] = ("Use of the force() method described in the previous section requires having " +
      "a direct dependency. However, it may be desirable to force a revision without introducing " +
      "that direct dependency").getBytes()
    sendPacket = new DatagramPacket(sendData, sendData.length, ipAddress, port)
    sendLocalHost = new DatagramPacket(sendData, sendData.length, localhost, port)
    targetAddress = new InetSocketAddress(ipAddress, port)
    localhostTarget = new InetSocketAddress(localhost, port)

    channel = DatagramChannel.open()
    channel.bind(new InetSocketAddress(9999))
    channel.configureBlocking(false)

    nioByteBuffer = ByteBuffer.allocate(sendData.length)
    nioByteBuffer.put(sendData)
    nioByteBuffer.flip()

    selector = Selector.open()
    channel.register(selector, SelectionKey.OP_WRITE)
  }

  @TearDown(Level.Trial)
  def tear(): Unit = {
    socket.close()
    channel.close()
    selector.close()
  }

  @Benchmark
  def sendBlocking(): Unit = {
    socket.send(sendPacket)
  }

  @Benchmark
  def sendLocalhostBlocking(): Unit = {
    socket.send(sendLocalHost)
  }

  @Benchmark
  def sendNio(): Unit = {
    if (channel.send(nioByteBuffer, targetAddress) == 0) {
      selector.select()
      assert(channel.send(nioByteBuffer, targetAddress) > 0)
    }
    nioByteBuffer.rewind()
  }

  @Benchmark
  def sendLocalhostNio(): Unit = {
    if (channel.send(nioByteBuffer, localhostTarget) == 0) {
      selector.select()
      assert(channel.send(nioByteBuffer, localhostTarget) > 0)
    }
    nioByteBuffer.rewind()
  }
}
