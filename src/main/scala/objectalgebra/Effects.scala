package objectalgebra

import objectalgebra.Effects._
import objectalgebra.Routing.Route

/**
 * Created by Paweł Sikora.
 */
trait Effects[E[_]] {

  def pure[V](value: V): E[V]

  def bind[P, V](source: E[P], f: P => E[V]): E[V]

}

object Effects {

  trait BasicEffects[V] {
    def map[R](f: V => R): BasicEffects[R] = flatMap(v => Pure(f(v)))

    def flatMap[R](f: V => BasicEffects[R]): BasicEffects[R] = BindImpl(this, f)
  }

  implicit object BasicEffectsInstance extends Effects[BasicEffects] {
    override def pure[V](value: V): BasicEffects[V] = Pure(value)

    def bind[P, V](source: BasicEffects[P], f: P => BasicEffects[V]): Bind[V] = BindImpl(source, f)
  }

  final case class Pure[V](value: V) extends BasicEffects[V]

  sealed trait Bind[V] extends BasicEffects[V] {
    type PreviousValueType
    val source: BasicEffects[PreviousValueType]
    val f: PreviousValueType => BasicEffects[V]
  }

  private final case class BindImpl[P, V](source: BasicEffects[P], f: P => BasicEffects[V]) extends Bind[V] {
    type PreviousValueType = P
  }

}

trait OperationWithParams[-C, +E[_], V] {
  def execute(c: C): E[V]
}

trait Routing[E[_]] {

  def route[V](operationWithParams: OperationWithParams[_, X forSome {type X[_]}, V]): E[V]
}


object Routing {

  object RoutingInstance extends Routing[Route] {
    def route[V](operationWithParams: OperationWithParams[_, X forSome {type X[_]}, V]): Route[V] = Route(operationWithParams)
  }

  final case class Route[V](operationWithParams: OperationWithParams[_, X forSome {type X[_]}, V])

}

object CalcPi extends OperationWithParams[Effects[BasicEffects], BasicEffects, Float] {
  override def execute(c: Effects[BasicEffects]): BasicEffects[Float] = c.pure(3.14f)
}

sealed trait UnionRoutingBasic[V] extends BasicEffects[V]

object Test {

  val eff = implicitly[Effects[BasicEffects]]

  def e1[E[_] : Effects]: E[Int] = {
    val eff = implicitly[Effects[E]]
    val p: E[Int] = eff.pure(10)
    eff.bind(p, (v: Int) => eff.pure(v + 10))
  }

  def e2(): BasicEffects[Float] = {
    import BasicEffectsInstance._
    for {
      p <- pure(10)
      p2 <- pure(11)
      p3 = p + p2
    } yield p3.toFloat
  }

  def e3[E[_] : Effects : Routing](): E[Int] = {
    val basicEffects = implicitly[Effects[E]]
    val routing = implicitly[Routing[E]]
    basicEffects.bind(routing.route(CalcPi), (f: Float) => basicEffects.pure(f.toInt))
  }

  def resolve[V](comp: BasicEffects[V]): V = {
    comp match {
      case fme: Bind[V] => resolve(fme.f(resolve(fme.source)))
      case p: Pure[V] => p.value
    }
  }

  def run() = {
    val be: BasicEffects[Int] = e1[BasicEffects]
    println(resolve(be))
  }
}