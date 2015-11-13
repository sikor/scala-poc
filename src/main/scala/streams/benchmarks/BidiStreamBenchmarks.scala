package streams.benchmarks

import java.util.concurrent.{Executors, ThreadFactory}

import monifu.concurrent.schedulers.AsyncScheduler
import monifu.concurrent.{Scheduler, UncaughtExceptionReporter}
import monifu.reactive.Ack.Continue
import monifu.reactive.Observable
import org.openjdk.jmh.annotations.Benchmark
import streams.BidiStream
import streams.BidiStream.{NoAction, ProcessingAction, PushToInput, PushToOutput}

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

/**
  *
  * Executor with one thread:
  * [info] Benchmark                                                Mode  Cnt   Score   Error  Units
  * [info] BidiStreamBenchmarks.BidiStreamBothDirections           thrpt    5   2.672 ± 0.173  ops/s
  * [info] BidiStreamBenchmarks.BidiStreamOneDirection             thrpt    5   5.709 ± 0.021  ops/s
  * [info] BidiStreamBenchmarks.MonifuMergeAndGroupBy              thrpt    5   3.598 ± 0.065  ops/s
  * [info] BidiStreamBenchmarks.MonifuMergeAndGroupByOneDirection  thrpt    5  10.654 ± 0.093  ops/s
  *
  * Improvement after removing case classes definitions from methods
  * [info] Benchmark                                                Mode  Cnt   Score   Error  Units
  * [info] BidiStreamBenchmarks.BidiStreamBothDirections           thrpt    5   4.615 ± 0.204  ops/s
  * [info] BidiStreamBenchmarks.BidiStreamOneDirection             thrpt    5  10.238 ± 0.393  ops/s
  * [info] BidiStreamBenchmarks.MonifuMergeAndGroupBy              thrpt    5   3.549 ± 0.089  ops/s
  * [info] BidiStreamBenchmarks.MonifuMergeAndGroupByOneDirection  thrpt    5  10.571 ± 0.326  ops/s
  *
  * Improvement after removing one unnecessary compareAndSet and defining untied state modifications
  * [info] Benchmark                                                Mode  Cnt   Score   Error  Units
  * [info] BidiStreamBenchmarks.BidiStreamBothDirections           thrpt    5   5.305 ± 0.174  ops/s
  * [info] BidiStreamBenchmarks.BidiStreamOneDirection             thrpt    5  11.248 ± 0.263  ops/s
  * [info] BidiStreamBenchmarks.MonifuMergeAndGroupBy              thrpt    5   3.568 ± 0.024  ops/s
  * [info] BidiStreamBenchmarks.MonifuMergeAndGroupByOneDirection  thrpt    5  10.676 ± 0.428  ops/s
  *
  * Executor with 4 threads:
  *
  * [info] Benchmark                                                Mode  Cnt   Score   Error  Units
  * [info] BidiStreamBenchmarks.BidiStreamBothDirections           thrpt    5   2.634 ± 0.200  ops/s
  * [info] BidiStreamBenchmarks.BidiStreamOneDirection             thrpt    5  10.835 ± 1.008  ops/s
  * [info] BidiStreamBenchmarks.MonifuMergeAndGroupBy              thrpt    5   2.876 ± 0.227  ops/s
  * [info] BidiStreamBenchmarks.MonifuMergeAndGroupByOneDirection  thrpt    5   9.907 ± 0.763  ops/s
  *
  */
object BidiStreamBenchmarks {
  val iterations = 1000000

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
      ExecutionContext.fromExecutor(Executors.newFixedThreadPool(4, new ThreadFactory {
        override def newThread(r: Runnable): Thread = {
          val th = new Thread(r)
          th.setDaemon(true)
          th.setName("benchmark-executor")
          th
        }
      })),
      UncaughtExceptionReporter.LogExceptionsToStandardErr
    )
}

class BidiStreamBenchmarks {

  import BidiStreamBenchmarks._

  //
  @Benchmark
  def BidiStreamOneDirection(): Unit = {
    def procIn(m: Any): ProcessingAction = {
      PushToInput(m)
    }
    val bidi = new BidiStream(procIn, _ => NoAction)
    val inObs = new AwaitableObserver()
    bidi.in().subscribe(inObs)
    Observable.range(0, iterations, 1).subscribe(bidi.in())
    bidi.out().onComplete()
    inObs.await(4.second)
  }

  @Benchmark
  def BidiStreamBothDirections(): Unit = {
    val state = new SimpleState
    def proc(m: Any): ProcessingAction = {
      if (m.asInstanceOf[Long] % 2 == 0) {
        PushToInput(m)
      } else {
        PushToOutput(m)
      }
    }
    val bidi = new BidiStream(proc, proc)
    val inObs = new AwaitableObserver()
    val outObs = new AwaitableObserver()
    bidi.in().subscribe(inObs)
    bidi.out().subscribe(outObs)
    Observable.range(0, iterations, 1).subscribe(bidi.in())
    Observable.range(0, iterations, 1).subscribe(bidi.out())
    inObs.await(4.second)
    outObs.await(4.second)
  }

  //  @Benchmark
  //  def MonifuMap(): Unit = {
  //    val inObs = new AwaitableObserver()
  //    Observable.range(0, iterations, 1).map(l => l).subscribe(inObs)
  //    inObs.await(4.second)
  //  }
  //
  //  @Benchmark
  //  def MonifuMapTwoStreams(): Unit = {
  //    val inObs = new AwaitableObserver()
  //    val outObs = new AwaitableObserver()
  //    Observable.range(0, iterations, 1).map(l => l).subscribe(inObs)
  //    Observable.range(0, iterations, 1).map(l => l).subscribe(outObs)
  //    inObs.await(4.second)
  //    outObs.await(4.second)
  //  }

  //  @Benchmark
  //  def MonifuMapTwoStreamsSynchronizedState(): Unit = {
  //    val inObs = new AwaitableObserver()
  //    val outObs = new AwaitableObserver()
  //    val state = new SynchState
  //    Observable.range(0, iterations, 1).map(state.updateState).subscribe(inObs)
  //    Observable.range(0, iterations, 1).map(state.updateState).subscribe(outObs)
  //    inObs.await(4.second)
  //    outObs.await(4.second)
  //  }
  //
  //  @Benchmark
  //  def MonifuMergeTwoStreams(): Unit = {
  //    val inObs = new AwaitableObserver()
  //    val state = new SimpleState
  //    val s1 = Observable.range(0, iterations, 1)
  //    val s2 = Observable.range(0, iterations, 1)
  //    Observable.merge(s1, s2).map(state.updateState).subscribe(inObs)
  //    inObs.await(4.second)
  //  }

  @Benchmark
  def MonifuMergeAndGroupBy(): Unit = {
    import MergeAndGroupByBidi._
    val inObs = new AwaitableObserver()
    val outObs = new AwaitableObserver()

    val s1 = Observable.range(0, iterations, 1).map(v => FromS1(v))
    val s2 = Observable.range(0, iterations, 1).map(v => FromS2(v))
    val state = new RoutingState
    Observable.merge(s1, s2).groupBy(state.routingFunc).subscribe {
      s => s.key match {
        case Input => s.map(v => v.value).subscribe(inObs); Continue
        case Output => s.map(v => v.value).subscribe(outObs); Continue
      }
    }
    inObs.await(4.second)
    outObs.await(4.second)
  }

  @Benchmark
  def MonifuMergeAndGroupByOneDirection(): Unit = {
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
    inObs.await(4.second)
    outObs.await(4.second)
  }
}
