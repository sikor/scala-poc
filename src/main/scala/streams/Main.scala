package streams

import _root_.streams.BidiStream.{PushToOutput, PushToInput, ProcessingAction, NoAction}
import monifu.concurrent.Implicits.globalScheduler
import monifu.reactive.Ack.Continue

import scala.concurrent.Future
import concurrent.duration._
import monifu.reactive._

/**
  * Created by Paweł Sikora.
  */
object Main {

  def main(args: Array[String]) {
    val observer = new Observer[Any] {
      override def onError(ex: Throwable): Unit = println("error")

      override def onComplete(): Unit = println("complete")

      override def onNext(elem: Any): Future[Ack] = {
        println(elem)
        Future.successful(Continue)
      }
    }

    def procIn(m: Any): ProcessingAction = {
      println("procIn-start")
      Thread.sleep(10)
      println("procIn-end")
      PushToInput(m)
    }

    def procOut(m: Any): ProcessingAction = {
      println("procOut-start")
      Thread.sleep(5)
      println("procOut-end")
      PushToOutput(m)
    }


    val bidi = new BidiStream(procIn, procOut)
    bidi.in().subscribe(m => {
      println("in " + m.toString)
      Continue
    })
    bidi.out().subscribe(m => {
      println("out " + m.toString)
      Continue
    })
    Observable.range(0, 1000, 1).take(100).subscribe(bidi.in())
    Observable.range(0, 1000, 1).take(100).subscribe(bidi.out())


    Thread.sleep(120000)
    println("terminating")
  }

}
