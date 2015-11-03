package folds


/**
  * @author Paweł Sikora
  */
object Routing {

  trait OperationWithParams[+E[_], V] {
    def execute: E[V]
  }

  final case class Route[+E[_], V](operationWithParams: OperationWithParams[E, V])

  trait RouteFold {
    def onRoute[E[_], V](r: Route[E, V]): V
  }

  trait RouteFactory[E[_]] {
    def route[V](r: Route[E, V]): E[V]
  }

}
