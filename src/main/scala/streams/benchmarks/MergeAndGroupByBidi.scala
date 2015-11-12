package streams.benchmarks

/**
  * @author Paweł Sikora
  */
object MergeAndGroupByBidi {

  sealed trait Source {
    def value: Long
  }

  case class FromS1(value: Long) extends Source

  case class FromS2(value: Long) extends Source

  sealed trait Route

  case object Input extends Route

  case object Output extends Route

  class RoutingState {
    def routingFunc(s: Source): Route = {
      if (s.value % 2 == 0) {
        Input
      } else {
        Output
      }
    }
  }


}
