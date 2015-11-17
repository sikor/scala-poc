package streams

import java.util.concurrent.{Executors, ThreadFactory}

import monifu.concurrent.schedulers.AsyncScheduler
import monifu.concurrent.{Scheduler, UncaughtExceptionReporter}
import monifu.reactive.Ack.Continue
import monifu.reactive.{Ack, Observable}
import streams.bidi.BidiStream
import BidiStream.{ProcessingAction, PushToInput, PushToOutput}

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future, Promise}

/**
  * Created by Paweł Sikora.
  */
object Main {

  implicit val globalScheduler: Scheduler =
    AsyncScheduler(
      Executors.newSingleThreadScheduledExecutor(new ThreadFactory {
        def newThread(r: Runnable): Thread = {
          val th = new Thread(r)
          th.setDaemon(true)
          th.setName("benchmark-scheduler")
          th
        }
      }),
      ExecutionContext.fromExecutor(Executors.newFixedThreadPool(1, new ThreadFactory {
        override def newThread(r: Runnable): Thread = {
          val th = new Thread(r)
          th.setDaemon(true)
          th.setName("benchmark-executor")
          th
        }
      })),
      UncaughtExceptionReporter.LogExceptionsToStandardErr
    )

  val iterations = 10000000000l

  def scheduleResponse(): Future[Ack] = {
    val promise = Promise[Ack]
    globalScheduler.execute(new Runnable {
      override def run(): Unit = {
        promise.success(Continue)
      }
    })
    promise.future
  }

  def main(args: Array[String]) {
    monifuMappingTest()
  }

  def bidiTest(): Unit = {
    val state = new SimpleState
    def procIn(m: Long): ProcessingAction[Long, Long] = {
      PushToInput(state.updateState(1))
    }
    def procOut(m: Long): ProcessingAction[Long, Long] = {
      PushToOutput(state.updateState(1))
    }
    val bidi = new BidiStream(procIn, procOut)
    val inObs = new AwaitableObserver(m => scheduleResponse())
    val outObs = new AwaitableObserver()
    bidi.in().subscribe(inObs)
    //    bidi.out().subscribe(outObs)
    bidi.out().onComplete()
    Observable.range(0, iterations, 1).subscribe(bidi.in())
    //    Observable.range(0, iterations, 1).subscribe(bidi.out())
    inObs.await(1000.second)
    //    outObs.await(1000.second)
  }

  def monifuTest(): Unit = {
    import MergeAndGroupByBidi._
    val inObs = new AwaitableObserver()
    val outObs = new AwaitableObserver()

    val s1 = Observable.range(0, iterations, 1).map(v => FromS1(v))
    val s2 = Observable.range(0, iterations, 1).take(0).map(v => FromS2(v))
    val state = new RoutingState
    Observable.merge(s1, s2).groupBy(state.routingFunc).subscribe {
      s => s.key match {
        case Input => s.map(v => v.value).subscribe(inObs); Continue
        case Output => s.map(v => v.value).subscribe(outObs); Continue
      }
    }
    inObs.await(1000.second)
    outObs.await(1000.second)
  }

  def monifuMappingTest(): Unit = {
    val inObs = new AwaitableObserver(m => scheduleResponse())
    val state = new SynchState
    Observable.range(0, iterations, 1).map(state.updateState).subscribe(inObs)
    inObs.await(1000.second)
  }
}
