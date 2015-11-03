package routingmonad

/**
 * Created by Paweł Sikora.
 */
trait Routing extends Effects {

  trait OperationWithParams[V] {
    def execute: Eff[V]
  }

  def route[V](operationWithParams: OperationWithParams[V]): Route[V] = Route(operationWithParams)

  final case class Route[V](operationWithParams: OperationWithParams[V]) extends Eff[V]

}
