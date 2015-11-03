package folds


/**
  * @author Paweł Sikora
  */
object Routing {

  trait OperationWithParams[-C, +E[_], V] {
    def execute(c: C): E[V]
  }

  final case class Route[V](operationWithParams: OperationWithParams[_, X forSome {type X[_]}, V])

  trait RouteFold[E[_]] {
    def onRoute[V](r: Route[V]): V
  }

  trait RouteFactory[E[_]] {
    def route[V](r: Route[V]): E[V]
  }

}
