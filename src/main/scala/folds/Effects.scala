package folds

import shapeless.ops.coproduct.Folder

/**
  * @author Paweł Sikora
  */
object Effects {

  final case class Pure[V](value: V)

  final case class Bind[E[_], S, V](source: E[S], f: S => E[V]) {
    type Source = S
    val src: E[Source] = source
    val fun: Source => E[V] = f
  }

  trait Effect[V] {
    type Folder

    def fold(folder: Folder): V
  }

  trait MonadicEffectsFold[E[_]] {
    def onPure[V](pure: Pure[V]): V = pure.value

    def onBind[S, V](bind: Bind[E, S, V]): V = fold(bind.f(fold(bind.source)))

    protected def fold[V](effect: E[V]): V

  }

  trait MonadicEffect[E[_], V] extends Effect[V] {

    def map[R](f: V => R): E[R] = flatMap(v => factory.pure(Pure(f(v))))

    def flatMap[R](f: V => E[R]): E[R] = factory.bind(Bind(self, f))

    def factory: MonadicEffectsFactory[E]

    protected def self: E[V]
  }

  trait MonadicEffectsFactory[E[_]] {
    def pure[V](pure: Pure[V]): E[V]

    def bind[S, V](p: Bind[E, S, V]): E[V]
  }

  object BasicEffectsFold extends MonadicEffectsFold[BasicEffect] {
    override protected def fold[V](effect: BasicEffect[V]): V = effect.fold(BasicEffectsFold)
  }

  trait BasicEffect[V] extends MonadicEffect[BasicEffect, V] {
    override type Folder = BasicEffectsFold.type

    override protected def self = this

    override def factory = BasicEffectFactory
  }

  object BasicEffectFactory extends MonadicEffectsFactory[BasicEffect] {
    implicit def pure[V](pure: Pure[V]): BasicEffect[V] = new BasicEffect[V] {
      override def fold(folder: BasicEffectsFold.type): V = folder.onPure(pure)
    }

    implicit def bind[S, V](b: Bind[BasicEffect, S, V]): BasicEffect[V] = new BasicEffect[V] {
      override def fold(folder: BasicEffectsFold.type): V = folder.onBind[S, V](b)
    }
  }

  object Execute {

    def example(): Unit = {
      import BasicEffectFactory._
      val ex = for {
        v <- Pure(10)
        v2 <- Pure(11)
      } yield v + v2
      val v = ex.fold(BasicEffectsFold)
      println(v)
    }

  }


}
