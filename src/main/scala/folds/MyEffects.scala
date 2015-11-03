package folds

import folds.Effects._
import folds.Routing.{OperationWithParams, Route}

/**
  * @author Paweł Sikora
  */
object MyEffects {

  type OpWithParams[V] = OperationWithParams[CoapEffect[V]]

  trait CoapFold extends MonadicEffectsFold[CoapEffect] {
    override def fold[V](effect: CoapEffect[V]): V = effect.fold(this)

    def onRoute[V](r: Route[OpWithParams[V]]): V = r.operationWithParams.execute.fold(this)
  }

  object SomeFold extends CoapFold {

  }

  object AnotherFold extends CoapFold {

  }

  trait CoapEffect[V] extends MonadicEffect[CoapEffect, V] {
    type Folder = CoapFold

    override protected def self: CoapEffect[V] = this

    override def factory: MonadicEffectsFactory[CoapEffect] = CoapEffectsFactory
  }

  object CoapEffectsFactory extends MonadicEffectsFactory[CoapEffect] {
    implicit override def pure[V](pure: Pure[V]): CoapEffect[V] = new CoapEffect[V] {
      override def fold(folder: Folder): V = folder.onPure(pure)
    }

    implicit override def bind[S, V](p: Bind[CoapEffect, S, V]): CoapEffect[V] = new CoapEffect[V] {
      override def fold(folder: Folder): V = folder.onBind(p)
    }

    implicit def route[V](r: Route[OpWithParams[V]]): CoapEffect[V] = new CoapEffect[V] {
      override def fold(folder: Folder): V = folder.onRoute(r)
    }
  }


  def example(): Int = {
    import CoapEffectsFactory._
    val op = new OpWithParams[Float] {
      override def execute: CoapEffect[Float] = Pure(3.14f)
    }
    val result: CoapEffect[Int] = for {
      x <- Pure(10)
      y <- Pure(11)
      z <- Route(op)
    } yield x + y * z.toInt
    AnotherFold.fold(result)
  }


}
