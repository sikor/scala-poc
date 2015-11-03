package folds

import folds.Effects.BasicEffects

/**
  * @author Paweł Sikora
  */
object Routing {

  trait OperationWithParams[-C, +E[_], V] {
    def execute(c: C): E[V]
  }

  final case class Route[V](operationWithParams: OperationWithParams[_, X forSome {type X[_]}, V])

  trait Rout

  trait MyEffectsSubset[V] extends BasicEffects[V]

}
