package folds

/**
  * @author Paweł Sikora
  */
object Effects {


  final case class Pure[V](value: V)

  object Bind {
    def apply[C[_], P, V](source: C[P], f: P => C[V]): Bind[C, V] = BindImpl(source, f)
  }

  sealed trait Bind[C[_], V] {
    type Source
    val source: C[Source]
    val f: Source => C[V]
  }

  final case class BindImpl[C[_], S, V](source: C[S], f: S => C[V]) extends Bind[C, V] {
    type Source = S
  }

  trait BasicEffectsFoldTemplate[E[_]] {
    def onPure[V](pure: Pure[V]): V

    def onBind[V](bind: Bind[E, V]): V
  }

  trait EffectsTemplate[F, V] {
    def fold(folder: F): V
  }

  trait BasicEffectsTemplate[F, E[_], V] extends EffectsTemplate[F, V] {

    def map[R](f: V => R): E[R] = flatMap(v => factory.pure(Pure(f(v))))

    def flatMap[R](f: V => E[R]): E[R] = factory.bind(BindImpl(self, f))

    def factory: BasicEffectsTemplateFactory[E]

    protected def self: E[V]
  }

  trait BasicEffectsTemplateFactory[E[_]] {
    def pure[V](pure: Pure[V]): E[V]

    def bind[V](p: Bind[E, V]): E[V]
  }

  trait BasicEffectsFold extends BasicEffectsFoldTemplate[BasicEffects]

  trait BasicEffects[V] extends BasicEffectsTemplate[BasicEffectsFold, BasicEffects, V] {
    override protected def self = this

    override def factory = BasicEffects
  }

  object BasicEffects extends BasicEffectsTemplateFactory[BasicEffects] {
    implicit def pure[V](pure: Pure[V]): BasicEffects[V] = new BasicEffects[V] {
      override def fold(folder: BasicEffectsFold): V = folder.onPure(pure)
    }

    implicit def bind[V](b: Bind[BasicEffects, V]): BasicEffects[V] = new BasicEffects[V] {
      override def fold(folder: BasicEffectsFold): V = folder.onBind(b)
    }
  }

  object Execute {

    object ExecuteFold extends BasicEffectsFold {
      override def onPure[V](pure: Pure[V]): V = pure.value

      override def onBind[V](bind: Bind[BasicEffects, V]): V = bind.f(bind.source.fold(ExecuteFold)).fold(ExecuteFold)
    }

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
