package folds

import folds.Effects._
import folds.Routing.{Route, RouteFactory, RouteFold}

/**
  * @author Paweł Sikora
  */
object MyEffects {

  trait CoapFold extends BasicEffectsFoldTemplate[CoapEffects] with RouteFold[CoapEffects]

  trait CoapEffects[V] extends BasicEffectsTemplate[CoapFold, CoapEffects, V] {
    override protected def self: CoapEffects[V] = this

    override def factory: BasicEffectsTemplateFactory[CoapEffects] = CoapEffectsFactory
  }

  object CoapEffectsFactory extends BasicEffectsTemplateFactory[CoapEffects] with RouteFactory[CoapEffects] {
    implicit override def pure[V](pure: Pure[V]): CoapEffects[V] = new CoapEffects[V] {
      override def fold(folder: CoapFold): V = folder.onPure(pure)
    }

    implicit override def bind[V](p: Bind[CoapEffects, V]): CoapEffects[V] = new CoapEffects[V] {
      override def fold(folder: CoapFold): V = folder.onBind(p)
    }

    implicit override def route[V](r: Route[V]): CoapEffects[V] = new CoapEffects[V] {
      override def fold(folder: CoapFold): V = folder.onRoute(r)
    }
  }

  object Execute extends CoapFold {
    override def onPure[V](pure: Pure[V]): V = ???

    override def onBind[V](bind: Bind[CoapEffects, V]): V = ???

    override def onRoute[V](r: Route[V]): V = ???
  }

}
