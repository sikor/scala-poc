package streams

import java.util.concurrent.{Executors, ThreadFactory}

import monifu.concurrent.schedulers.AsyncScheduler
import monifu.concurrent.{Scheduler, UncaughtExceptionReporter}
import monifu.reactive.Observable
import streams.BidiStream.{ProcessingAction, PushToInput, PushToOutput}
import streams.benchmarks.{AwaitableObserver, SimpleState}

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
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

  def main(args: Array[String]) {
    val iterations = 10000000000l
    val state = new SimpleState
    def procIn(m: Any): ProcessingAction = {
      PushToInput(state.updateState(1))
    }
    def procOut(m: Any): ProcessingAction = {
      PushToOutput(state.updateState(1))
    }
    val bidi = new BidiStream(procIn, procOut)
    val inObs = new AwaitableObserver()
    val outObs = new AwaitableObserver()
    bidi.in().subscribe(inObs)
    bidi.out().subscribe(outObs)
    Observable.range(0, iterations, 1).subscribe(bidi.in())
    Observable.range(0, iterations, 1).subscribe(bidi.out())
    inObs.await(1000.second)
    outObs.await(1000.second)
  }

}
