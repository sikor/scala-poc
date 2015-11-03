package folds

/**
  * @author Paweł Sikora
  */
object Effects {



  final case class Pure[V](value: V)

  sealed trait Bind[C[_], V] {
    type PreviousValueType
    val source: C[PreviousValueType]
    val f: PreviousValueType => C[V]
  }

  final case class BindImpl[C[_], P, V](source: C[P], f: P => C[V]) extends Bind[C, V] {
    type PreviousValueType = P
  }

  trait BasicEffectsFold {
    def onPure[V](pure: Pure[V]): V

    def onBind[V](bind: Bind[BasicEffects, V]): V
  }

  trait BasicEffects[V] {
    def fold(folder: BasicEffectsFold): V

    def map[R](f: V => R): BasicEffects[R] = flatMap(v => BasicEffects.pure(f(v)))

    def flatMap[R](f: V => BasicEffects[R]): BasicEffects[R] = BasicEffects.bind(this, f)
  }

  trait OnlyBasicEffectsFold extends BasicEffectsFold

  trait OnlyBasicEffects[V] extends BasicEffects[V]

  object BasicEffects {
    def pure[V](value: V): BasicEffects[V] = new BasicEffects[V] {
      override def fold(folder: BasicEffectsFold): V = folder.onPure(Pure(value))
    }

    def bind[P, V](source: BasicEffects[P], f: P => BasicEffects[V]): BasicEffects[V] = new BasicEffects[V] {
      override def fold(folder: BasicEffectsFold): V = folder.onBind(BindImpl(source, f))
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
        v <- pure(10)
        v2 <- pure(11)
      } yield v + v2
      val v = ex.fold(ExecuteFold)
      println(v)
    }

  }


}
