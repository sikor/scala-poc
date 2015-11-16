package streams.benchmarks

import java.util.concurrent.{TimeUnit, Executors, ThreadFactory}

import monifu.concurrent.schedulers.AsyncScheduler
import monifu.concurrent.{Scheduler, UncaughtExceptionReporter}
import monifu.reactive.Ack.Continue
import monifu.reactive.{Ack, Observable}
import org.openjdk.jmh.annotations._
import streams.BidiStream
import streams.BidiStream.{NoAction, ProcessingAction, PushToInput, PushToOutput}

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future, Promise}

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
  * Improvement after removing one unnecessary compareAndSet
  * [info] Benchmark                                                Mode  Cnt   Score   Error  Units
  * [info] BidiStreamBenchmarks.BidiStreamBothDirections           thrpt    5   5.305 ± 0.174  ops/s
  * [info] BidiStreamBenchmarks.BidiStreamOneDirection             thrpt    5  11.248 ± 0.263  ops/s
  * [info] BidiStreamBenchmarks.MonifuMergeAndGroupBy              thrpt    5   3.568 ± 0.024  ops/s
  * [info] BidiStreamBenchmarks.MonifuMergeAndGroupByOneDirection  thrpt    5  10.676 ± 0.428  ops/s
  *
  * Improvement after fast loop optimization
  * [info] Benchmark                                                   Mode  Cnt   Score   Error  Units
  * [info] BidiStreamBenchmarks.BidiStreamBothDirections              thrpt    5   8.655 ± 0.447  ops/s
  * [info] BidiStreamBenchmarks.BidiStreamOneDirection                thrpt    5  19.052 ± 0.596  ops/s
  * [info] BidiStreamBenchmarks.MonifuMapTwoStreamsSynchronizedState  thrpt    5  27.948 ± 0.695  ops/s
  * [info] BidiStreamBenchmarks.MonifuMergeAndGroupBy                 thrpt    5   3.915 ± 0.065  ops/s
  * [info] BidiStreamBenchmarks.MonifuMergeAndGroupByOneDirection     thrpt    5  11.774 ± 0.268  ops/s
  *
  *
  * Executor with 4 threads:
  *
  * [info] Benchmark                                                Mode  Cnt   Score   Error  Units
  * [info] BidiStreamBenchmarks.BidiStreamBothDirections           thrpt    5   2.634 ± 0.200  ops/s
  * [info] BidiStreamBenchmarks.BidiStreamOneDirection             thrpt    5  10.835 ± 1.008  ops/s
  * [info] BidiStreamBenchmarks.MonifuMergeAndGroupBy              thrpt    5   2.876 ± 0.227  ops/s
  * [info] BidiStreamBenchmarks.MonifuMergeAndGroupByOneDirection  thrpt    5   9.907 ± 0.763  ops/s
  *
  * Test how our Stream utilize executor.
  * Executor with 8 threads. This executor is shared among 16 threads of jmh: jmh:run -i 5 -wi 5 -f1 -t16
  * [info] Benchmark                                                   Mode  Cnt   Score   Error  Units
  * [info] BidiStreamBenchmarks.BidiStreamBothDirections              thrpt    5  17.042 ± 1.207  ops/s
  * [info] BidiStreamBenchmarks.BidiStreamOneDirection                thrpt    5  43.059 ± 1.860  ops/s
  * [info] BidiStreamBenchmarks.MonifuMapTwoStreamsSynchronizedState  thrpt    5  56.310 ± 2.330  ops/s
  * [info] BidiStreamBenchmarks.MonifuMergeAndGroupBy                 thrpt    5   9.881 ± 0.375  ops/s
  * [info] BidiStreamBenchmarks.MonifuMergeAndGroupByOneDirection     thrpt    5  37.383 ± 0.740  ops/s
  *
  */
object BidiStreamBenchmarks {
  val iterations = 1000

  val defaultExecutor = ExecutionContext.global

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

  def scheduleResponse(): Future[Ack] = {
    val promise = Promise[Ack]
    defaultExecutor.execute(new Runnable {
      override def run(): Unit = {
        promise.success(Continue)
      }
    })
    promise.future
  }
}


class BidiStreamBenchmarks {

  import BidiStreamBenchmarks._


  @BenchmarkMode(Array(Mode.AverageTime, Mode.Throughput))
  @OutputTimeUnit(TimeUnit.MICROSECONDS)
  @OperationsPerInvocation(1000)
  @Benchmark
  def BidiStreamOneDirection(): Unit = {
    def procIn(m: Long): ProcessingAction[Long, Long] = {
      PushToInput(m)
    }
    val bidi = new BidiStream(procIn, (_: Any) => NoAction)
    val inObs = new AwaitableObserver()
    bidi.in().subscribe(inObs)
    Observable.range(0, iterations, 1).subscribe(bidi.in())
    bidi.out().onComplete()
    inObs.await(4.second)
  }

  @OperationsPerInvocation(1000)
  @Benchmark
  def BidiStreamOneDirectionAsynch(): Unit = {
    def procIn(m: Long): ProcessingAction[Long, Long] = {
      PushToInput(m)
    }
    val bidi = new BidiStream(procIn, (_: Any) => NoAction)
    val inObs = new AwaitableObserver(m => scheduleResponse())
    bidi.in().subscribe(inObs)
    Observable.range(0, iterations, 1).subscribe(bidi.in())
    bidi.out().onComplete()
    inObs.await(4.second)
  }

  @OperationsPerInvocation(2000)
  @Benchmark
  def BidiStreamTwoDirectionsAsynch(): Unit = {
    def proc(m: Long): ProcessingAction[Long, Long] = {
      if (m % 2 == 0) {
        PushToInput(m)
      } else {
        PushToOutput(m)
      }
    }
    val bidi = new BidiStream(proc, proc)
    val inObs = new AwaitableObserver(m => scheduleResponse())
    val outObs = new AwaitableObserver(m => scheduleResponse())
    bidi.in().subscribe(inObs)
    bidi.out().subscribe(outObs)
    Observable.range(0, iterations, 1).subscribe(bidi.in())
    Observable.range(0, iterations, 1).subscribe(bidi.out())
    inObs.await(4.second)
    outObs.await(4.second)
  }

  @Benchmark
  @OperationsPerInvocation(2000)
  def BidiStreamOneWayAsynchResponse(): Unit = {
    def proc(m: Long): ProcessingAction[Long, Long] = {
      if (m % 2 == 0) {
        PushToInput(m)
      } else {
        PushToOutput(m)
      }
    }
    val bidi = new BidiStream(proc, proc)
    val inObs = new AwaitableObserver(m => scheduleResponse())
    val outObs = new AwaitableObserver()
    bidi.in().subscribe(inObs)
    bidi.out().subscribe(outObs)
    Observable.range(0, iterations, 1).subscribe(bidi.in())
    Observable.range(0, iterations, 1).subscribe(bidi.out())
    inObs.await(4.second)
    outObs.await(4.second)
  }

  @Benchmark
  @OperationsPerInvocation(2000)
  def BidiStreamBothDirections(): Unit = {
    def proc(m: Long): ProcessingAction[Long, Long] = {
      if (m % 2 == 0) {
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

  @Benchmark
  @OperationsPerInvocation(1000)
  def MonifuMapOneStreamAsynchResponse(): Unit = {
    val inObs = new AwaitableObserver(m => scheduleResponse())
    val state = new SimpleState
    Observable.range(0, iterations, 1).map(state.updateState).subscribe(inObs)
    inObs.await(4.second)
  }

  @Benchmark
  @OperationsPerInvocation(2000)
  def MonifuMapTwoStreamsSynchronizedState(): Unit = {
    val inObs = new AwaitableObserver()
    val outObs = new AwaitableObserver()
    val state = new SynchState
    Observable.range(0, iterations, 1).map(state.updateState).subscribe(inObs)
    Observable.range(0, iterations, 1).map(state.updateState).subscribe(outObs)
    inObs.await(4.second)
    outObs.await(4.second)
  }

  @Benchmark
  @OperationsPerInvocation(2000)
  def MonifuMapTwoStreamsSynchronizedStateAsynchResponse(): Unit = {
    val inObs = new AwaitableObserver(m => scheduleResponse())
    val outObs = new AwaitableObserver(m => scheduleResponse())
    val state = new SynchState
    Observable.range(0, iterations, 1).map(state.updateState).subscribe(inObs)
    Observable.range(0, iterations, 1).map(state.updateState).subscribe(outObs)
    inObs.await(4.second)
    outObs.await(4.second)
  }

  @Benchmark
  @OperationsPerInvocation(2000)
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
  @OperationsPerInvocation(2000)
  def MonifuMergeAndGroupByAsynchResponse(): Unit = {
    import MergeAndGroupByBidi._
    val inObs = new AwaitableObserver(m => scheduleResponse())
    val outObs = new AwaitableObserver(m => scheduleResponse())

    val s1 = Observable.range(0, iterations, 1).map(v => FromS1(v))
    val s2 = Observable.range(0, iterations, 1).map(v => FromS2(v))
    val state = new RoutingState
    Observable.merge(s1, s2).groupBy(state.routingFunc).subscribe {
      s => s.key match {
        case Input => s.map(v => v.value).subscribe(inObs); Continue
        case Output => s.map(v => v.value).subscribe(outObs); Continue
      }
    }
    inObs.await(10.second)
    outObs.await(10.second)
  }

  @Benchmark
  @OperationsPerInvocation(2000)
  def MonifuMergeAndGroupByOneWayAsynchResponse(): Unit = {
    import MergeAndGroupByBidi._
    val inObs = new AwaitableObserver(m => scheduleResponse())
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
    inObs.await(10.second)
    outObs.await(10.second)
  }

  @Benchmark
  @OperationsPerInvocation(1000)
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
