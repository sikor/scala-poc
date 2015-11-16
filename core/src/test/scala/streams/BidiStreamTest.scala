package streams

import java.util
import java.util.concurrent.ConcurrentLinkedQueue

import monifu.concurrent.Implicits.globalScheduler
import monifu.reactive.Ack.Continue
import monifu.reactive.{Ack, Observable, Observer}
import org.scalatest.{FunSuite, Matchers}
import streams.BidiStream.{ProcessingAction, PushToInput, PushToOutput}

import scala.collection.JavaConversions._
import scala.concurrent.duration._
import scala.concurrent.{Await, Future, Promise}
import scala.util.Random

/**
  * Created by Paweł Sikora.
  */
class BidiStreamTest extends FunSuite with Matchers {

  class AwaitableObserver(onNextFunc: Any => Future[Ack] = _ => Continue) extends Observer[Any] {
    private val completed = Promise[Unit]

    def onFinished: Future[Unit] = completed.future

    def await(duration: FiniteDuration): Unit = {
      // await for result to throw exception if any occurred
      Await.result(onFinished, duration)
    }

    override def onNext(elem: Any): Future[Ack] = onNextFunc(elem)

    override def onError(ex: Throwable): Unit = completed.failure(ex)

    override def onComplete(): Unit = completed.success(())
  }

  test("Processor functions should be called atomically") {
    val processingEvents = new ConcurrentLinkedQueue[String]()
    val random = new Random()
    def procIn(m: Long): ProcessingAction[Long, Long] = {
      processingEvents.add("procIn-start")
      Thread.sleep(1 + random.nextInt(9))
      processingEvents.add("procIn-end")
      PushToInput(m)
    }

    def procOut(m: Long): ProcessingAction[Long, Long] = {
      processingEvents.add("procOut-start")
      Thread.sleep(5 + random.nextInt(5))
      processingEvents.add("procOut-end")
      PushToOutput(m)
    }
    val bidi = new BidiStream(procIn, procOut)
    val inObs = new AwaitableObserver()
    val outObs = new AwaitableObserver()
    bidi.in().subscribe(inObs)
    bidi.out().subscribe(outObs)
    Observable.range(0, 100, 1).subscribe(bidi.in())
    Observable.range(0, 100, 1).subscribe(bidi.out())
    inObs.await(4.second)
    outObs.await(4.second)
    processingEvents.size() should be(400)
    val eventsArr = processingEvents.toArray
    for (i <- Range(0, processingEvents.size() - 1, 2)) {
      assert((eventsArr(i) == "procIn-start" && eventsArr(i + 1) == "procIn-end") ||
        (eventsArr(i) == "procOut-start" && eventsArr(i + 1) == "procOut-end"))
    }
  }

  test("Messages from one source routed to one sink") {
    val inputMessages = new ConcurrentLinkedQueue[Long]()
    val outputMessages = new ConcurrentLinkedQueue[Long]()
    def procIn(m: Long): ProcessingAction[Long, Long] = {
      PushToInput(m)
    }

    def procOut(m: Long): ProcessingAction[Long, Long] = {
      PushToOutput(m)
    }
    val bidi = new BidiStream(procIn, procOut)
    val inObs = new AwaitableObserver(m => {
      inputMessages.add(m.asInstanceOf[Long])
      Continue
    })
    val outObs = new AwaitableObserver(m => {
      outputMessages.add(m.asInstanceOf[Long])
      Continue
    })
    bidi.in().subscribe(inObs)
    bidi.out().subscribe(outObs)
    Observable.range(0, 1000, 1).subscribe(bidi.in())
    Observable.range(1000, 2000, 1).subscribe(bidi.out())
    inObs.await(4.second)
    outObs.await(4.second)
    inputMessages.size() should be(1000)
    outputMessages.size() should be(1000)
    val inputMessagesArr = inputMessages.toArray
    val outputMessagesArr = outputMessages.toArray

    for (i <- Range(0, 1000)) {
      assert(inputMessagesArr(i).asInstanceOf[Long] == i)
      assert(outputMessagesArr(i).asInstanceOf[Long] == i + 1000)
    }
  }

  test("All incoming messages pushed to one sink") {
    val inputMessages = new util.HashSet[Long]()
    val outputMessages = new util.HashSet[Long]()
    def procIn(m: Long): ProcessingAction[Long, Long] = {
      PushToInput(m)
    }

    def procOut(m: Long): ProcessingAction[Long, Long] = {
      PushToInput(m)
    }
    val bidi = new BidiStream(procIn, procOut)
    val inObs = new AwaitableObserver(m => {
      inputMessages.add(m.asInstanceOf[Long])
      Continue
    })
    val outObs = new AwaitableObserver(m => {
      outputMessages.add(m.asInstanceOf[Long])
      Continue
    })
    bidi.in().subscribe(inObs)
    bidi.out().subscribe(outObs)
    Observable.range(0, 1000, 1).subscribe(bidi.in())
    Observable.range(1000, 2000, 1).subscribe(bidi.out())
    inObs.await(4.second)
    outObs.await(4.second)
    inputMessages.toSet.size should be(2000)
    outputMessages.size() should be(0)
  }

  test("Should work with only one input and one output") {
    val inputMessages = new ConcurrentLinkedQueue[Long]()
    def procIn(m: Long): ProcessingAction[Long, Long] = {
      PushToInput(m)
    }

    def procOut(m: Long): ProcessingAction[Long, Long] = {
      PushToOutput(m)
    }
    val bidi = new BidiStream(procIn, procOut)
    val inObs = new AwaitableObserver(m => {
      inputMessages.add(m.asInstanceOf[Long])
      Continue
    })
    bidi.in().subscribe(inObs)
    Observable.range(0, 100, 1).subscribe(bidi.in())
    bidi.out().onComplete()
    inObs.await(4.second)
    assert(inputMessages.size() == 100)
  }

  test("Should work with asynch responses") {
    val inputMessages = new ConcurrentLinkedQueue[Long]()
    val outputMessages = new ConcurrentLinkedQueue[Long]()
    val random = new Random()
    def procIn(m: Long): ProcessingAction[Long, Long] = {
      PushToInput(m)
    }

    def procOut(m: Long): ProcessingAction[Long, Long] = {
      PushToOutput(m)
    }
    def scheduleResponse(): Future[Ack] = {
      val promise = Promise[Ack]
      globalScheduler.execute(new Runnable {
        override def run(): Unit = {
          Thread.sleep(random.nextInt(4))
          promise.success(Continue)
        }
      })
      promise.future
    }
    val bidi = new BidiStream(procIn, procOut)
    val inObs = new AwaitableObserver(m => {
      inputMessages.add(m.asInstanceOf[Long])
      scheduleResponse()
    })
    val outObs = new AwaitableObserver(m => {
      outputMessages.add(m.asInstanceOf[Long])
      scheduleResponse()
    })
    bidi.in().subscribe(inObs)
    bidi.out().subscribe(outObs)
    Observable.range(0, 100, 1).subscribe(bidi.in())
    Observable.range(100, 200, 1).subscribe(bidi.out())
    inObs.await(4.second)
    outObs.await(4.second)
    inputMessages.size() should be(100)
    outputMessages.size() should be(100)
    val inputMessagesArr = inputMessages.toArray
    val outputMessagesArr = outputMessages.toArray

    for (i <- Range(0, 100)) {
      assert(inputMessagesArr(i).asInstanceOf[Long] == i)
      assert(outputMessagesArr(i).asInstanceOf[Long] == i + 100)
    }
  }

}