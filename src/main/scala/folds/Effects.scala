package folds

/**
  * @author Paweł Sikora
  */
object Effects {


  final case class Pure[V](value: V)

  final case class Bind[ES[_], S, EV](source: ES[S], f: S => EV) {
    type Source = S
    val src: ES[Source] = source
    val fun: Source => EV = f
  }

  trait BasicEffectsFoldTemplate[F] {
    def onPure[V](pure: Pure[V]): V = pure.value

    def onBind[ES[_] <: Effect[F, S], S, V, EV <: Effect[F, V]](bind: Bind[ES, S, EV]): V =
      bind.f(bind.source.fold(fldr)).fold(fldr)

    def fldr: F
  }

  trait Effect[F, V] {
    def fold(folder: F): V
  }

  trait BasicEffectsTemplate[F, E[_], V] extends Effect[F, V] {

    def map[R](f: V => R): E[R] = flatMap(v => factory.pure(Pure(f(v))))

    def flatMap[R](f: V => E[R]): E[R] = factory.bind(Bind(self, f))

    def factory: BasicEffectsTemplateFactory[E]

    protected def self: E[V]
  }

  trait BasicEffectsTemplateFactory[E[_]] {
    def pure[V](pure: Pure[V]): E[V]

    def bind[S, V](p: Bind[E, S, E[V]]): E[V]
  }

  trait BasicEffectsFold extends BasicEffectsFoldTemplate[BasicEffectsFold]{
    override def fldr = this
  }

  trait BasicEffects[V] extends BasicEffectsTemplate[BasicEffectsFold, BasicEffects, V] {
    override protected def self = this

    override def factory = BasicEffects
  }

  object BasicEffects extends BasicEffectsTemplateFactory[BasicEffects] {
    implicit def pure[V](pure: Pure[V]): BasicEffects[V] = new BasicEffects[V] {
      override def fold(folder: BasicEffectsFold): V = folder.onPure(pure)
    }

    implicit def bind[S, V](b: Bind[BasicEffects, S, BasicEffects[V]]): BasicEffects[V] = new BasicEffects[V] {
      override def fold(folder: BasicEffectsFold): V = folder.onBind[BasicEffects, S, V, BasicEffects[V]](b)
    }
  }

  object Execute {

    object ExecuteFold extends BasicEffectsFold

    def example(): Unit = {
      import BasicEffects._
      val ex = for {
        v <- Pure(10)
        v2 <- Pure(11)
      } yield v + v2
      val v = ex.fold(ExecuteFold)
      println(v)
    }

  }


}
