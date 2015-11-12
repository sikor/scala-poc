package streams.benchmarks

import monifu.concurrent.Implicits.globalScheduler
import monifu.reactive.Observable
import org.openjdk.jmh.annotations.Benchmark
import streams.BidiStream
import streams.BidiStream.{PushToOutput, NoAction, ProcessingAction, PushToInput}

import scala.concurrent.duration._

/**
  * Created by Paweł Sikora.
  */
object BidiStreamBenchmarks {
  val iterations = 1000000
}

class BidiStreamBenchmarks {

  import BidiStreamBenchmarks._


  @Benchmark
  def bidiStreamStraightLine(): Unit = {
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
  def bidiStreamBothDirections(): Unit = {
    val state = new SimpleState
    def procIn(m: Any): ProcessingAction = {
      PushToInput(state.updateState(m.asInstanceOf[Long]))
    }
    def procOut(m: Any): ProcessingAction = {
      PushToOutput(state.updateState(m.asInstanceOf[Long]))
    }
    val bidi = new BidiStream(procIn, procOut)
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
  def rawMonifuStraightLine(): Unit = {
    val inObs = new AwaitableObserver()
    Observable.range(0, iterations, 1).map(l => l).subscribe(inObs)
    inObs.await(4.second)
  }

  @Benchmark
  def rawMonifuBothDirections(): Unit = {
    val inObs = new AwaitableObserver()
    val outObs = new AwaitableObserver()
    Observable.range(0, iterations, 1).map(l => l).subscribe(inObs)
    Observable.range(0, iterations, 1).map(l => l).subscribe(outObs)
    inObs.await(4.second)
    outObs.await(4.second)
  }

  @Benchmark
  def rawMonifuBothDirectionsSynchronizedState(): Unit = {
    val inObs = new AwaitableObserver()
    val outObs = new AwaitableObserver()
    val state = new SynchState
    Observable.range(0, iterations, 1).map(state.updateState).subscribe(inObs)
    Observable.range(0, iterations, 1).map(state.updateState).subscribe(outObs)
    inObs.await(4.second)
    outObs.await(4.second)
  }
}
