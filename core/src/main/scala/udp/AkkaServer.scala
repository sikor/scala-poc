package udp

import java.net.InetSocketAddress

import akka.actor.{Props, Actor, ActorRef, ActorSystem}
import akka.io.Inet.SO.{ReceiveBufferSize, SendBufferSize}
import akka.io.Udp.{CommandFailed, Event, Send}
import akka.io.{IO, Udp}
import akka.util.ByteString

import scala.collection.immutable.List
import scala.concurrent.Await
import scala.concurrent.duration._

/**
  * @author Paweł Sikora
  *
  *         180k - 190k req p second with remote clients. (with ack 120k p second)
  *         155k localhost
  */
object AkkaServer {


  class ServerActor(bindAddr: InetSocketAddress) extends Actor {

    import context.system

    IO(Udp) ! Udp.Bind(self, bindAddr, List(new SendBufferSize(66000 * 100), new ReceiveBufferSize(66000 * 100)))

    private val data = ByteString("dupa")

    private val stats: Statistics = new Statistics

    override def receive: Receive = {
      case Udp.Bound(_) => context.become(ready(sender()))
    }

    object Ack extends Event

    def ready(socket: ActorRef): Receive = {
      case Udp.Received(_, sender) => socket ! Send(data, sender)
        stats.onSent()
      case CommandFailed(cmd) => println(cmd.failureMessage)
    }

  }

  def main(args: Array[String]): Unit = {
    var bindAddr: InetSocketAddress = null
    if (args.length == 2) {
      bindAddr = new InetSocketAddress(args(0), Integer.valueOf(args(1)))
    } else {
      bindAddr = new InetSocketAddress(9876)
    }
    val system = ActorSystem("coap-example")
    system.actorOf(Props(classOf[ServerActor], bindAddr))

    Await.ready(system.whenTerminated, 1 day)
  }


}
