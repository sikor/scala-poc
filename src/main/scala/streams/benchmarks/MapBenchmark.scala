package streams.benchmarks

import java.util
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

import org.openjdk.jmh.annotations._

/**
  * Created by Paweł Sikora.
  */

object MapBenchmark {

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
class MapBenchmark {

  import MapBenchmark._

  var concMap: ConcurrentHashMap[HashCodeHolder, Int] = _
  var stdMap: util.HashMap[HashCodeHolder, Int] = _
  var items: Array[HashCodeHolder] = _
  val initialMapSize = 1000000
  val itemsSize = 10000000
  val globalCounter = new AtomicInteger(0)

  @Setup(Level.Trial)
  def setupBench(): Unit = {
    items = Array.ofDim(itemsSize)
    for (i <- Range(0, itemsSize)) {
      items.update(i, new HashCodeHolder(("sugar" + i + "sugar").hashCode))
    }
  }

  @Setup(Level.Iteration)
  def up(): Unit = {
    concMap = new ConcurrentHashMap[HashCodeHolder, Int]()
    stdMap = new util.HashMap[HashCodeHolder, Int]()
    for (i <- Range(0, initialMapSize)) {
      concMap.put(items(i), i)
      stdMap.put(items(i), i)
    }
    globalCounter.set(0)
    System.gc()
  }

  @Benchmark
  def putAndRemoveToConcMap(state: ThreadState): Unit = {
    val counter = globalCounter.incrementAndGet()
    concMap.putIfAbsent(items(counter + initialMapSize), state.counter + 1)
    concMap.remove(items(counter))
  }

  @Benchmark
  def putAndRemoveToStdMap(state: ThreadState): Unit = {
    val counter = globalCounter.incrementAndGet()
    stdMap.putIfAbsent(items(counter + initialMapSize), state.counter + 1)
    stdMap.remove(items(counter))
  }

  @Benchmark
  def removeFromConcMap(state: ThreadState): Unit = {
    concMap.remove(state.counter)
    state.counter = state.counter + 1
  }
}
