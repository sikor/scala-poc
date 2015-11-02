package routingmonad

/**
  * Created by Paweł Sikora.
  */
object Routing {

  trait OperationWithParams[V] {
    def execute: V
  }

  def route[O <: OperationWithParams[V], V](operationWithParams: O): Route[V] = Route(operationWithParams)

  case class Route[V] private[Routing](operationWithParams: OperationWithParams[V]) extends Eff[V]

}
