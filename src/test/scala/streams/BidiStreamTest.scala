package streams

import java.util
import java.util.concurrent.ConcurrentLinkedQueue

import monifu.reactive.Ack.Continue
import monifu.reactive.{Ack, Observer, Observable}
import org.scalatest.{FlatSpec, Matchers, FunSuite}
import streams.BidiStream.{PushToOutput, PushToInput, ProcessingAction}

import scala.concurrent.{Future, Await, Promise}
import concurrent.duration._
import monifu.concurrent.Implicits.globalScheduler

import scala.util.Random
import scala.collection.JavaConversions._

/**
  * Created by Paweł Sikora.
  */
class BidiStreamTest extends FlatSpec with Matchers {

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

  "Processor functions" should "be called atomically" in {
    val processingEvents = new ConcurrentLinkedQueue[String]()
    val random = new Random()
    def procIn(m: Any): ProcessingAction = {
      processingEvents.add("procIn-start")
      Thread.sleep(1 + random.nextInt(9))
      processingEvents.add("procIn-end")
      PushToInput(m)
    }

    def procOut(m: Any): ProcessingAction = {
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

  "All incoming messages" should "be pushed" in {
    val inputMessages = new ConcurrentLinkedQueue[Long]()
    val outputMessages = new ConcurrentLinkedQueue[Long]()
    val random = new Random()
    def procIn(m: Any): ProcessingAction = {
      Thread.sleep(random.nextInt(10))
      PushToInput(m)
    }

    def procOut(m: Any): ProcessingAction = {
      Thread.sleep(random.nextInt(10))
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

  "All incoming messages" should "be pushed to one subscriber" in {
    val inputMessages = new util.HashSet[Long]()
    val outputMessages = new util.HashSet[Long]()
    val random = new Random()
    def procIn(m: Any): ProcessingAction = {
      PushToInput(m)
    }

    def procOut(m: Any): ProcessingAction = {
      PushToInput(m)
    }
    val bidi = new BidiStream(procIn, procOut)
    val inObs = new AwaitableObserver(m => {
      Thread.sleep(random.nextInt(5))
      inputMessages.add(m.asInstanceOf[Long])
      Continue
    })
    val outObs = new AwaitableObserver(m => {
      outputMessages.add(m.asInstanceOf[Long])
      Continue
    })
    bidi.in().subscribe(inObs)
    bidi.out().subscribe(outObs)
    Observable.range(0, 100, 1).subscribe(bidi.in())
    Observable.range(100, 200, 1).subscribe(bidi.out())
    inObs.await(4.second)
    outObs.await(4.second)
    inputMessages.toSet.size should be(200)
    outputMessages.size() should be(0)
  }
}
