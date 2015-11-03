package folds

import folds.Effects._
import folds.Routing.{Route, RouteFactory, RouteFold}

/**
  * @author Paweł Sikora
  */
object MyEffects {

  trait CoapFold extends BasicEffectsFoldTemplate[CoapFold] with RouteFold {
    def fldr: CoapFold = this
  }

  trait CoapEffects[V] extends BasicEffectsTemplate[CoapFold, CoapEffects, V] {
    override protected def self: CoapEffects[V] = this

    override def factory: BasicEffectsTemplateFactory[CoapEffects] = CoapEffectsFactory
  }

  object CoapEffectsFactory extends BasicEffectsTemplateFactory[CoapEffects] with RouteFactory[CoapEffects] {
    implicit override def pure[V](pure: Pure[V]): CoapEffects[V] = new CoapEffects[V] {
      override def fold(folder: CoapFold): V = folder.onPure(pure)
    }


    implicit override def bind[S, V](p: Bind[CoapEffects, S, CoapEffects[V]]): CoapEffects[V] = new CoapEffects[V] {
      override def fold(folder: CoapFold): V = folder.onBind(p)
    }

    implicit override def route[V](r: Route[CoapEffects, V]): CoapEffects[V] = new CoapEffects[V] {
      override def fold(folder: CoapFold): V = folder.onRoute(r)
    }
  }


}
