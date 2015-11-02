package routingmonad

import routingmonad.Eff.{FlatMappedEff, FlatMappedEffInternal, Pure}

/**
 * Created by Paweł Sikora.
 */
object Eff {

  def ret[V](value: V) = Pure(value)

  case class Pure[V](value: V) extends Eff[V] {
    //    override def map[R](f: V => R): Eff[R] = Pure(f(value))
    //
    //    override def flatMap[R](f: V => Eff[R]): Eff[R] = f(value)
  }

  trait FlatMappedEff[V] extends Eff[V] {
    type PreviousValueType
    type NewValueType = V

    def previousEff: Eff[PreviousValueType]

    def fMapFun: PreviousValueType => Eff[V]
  }

  case class FlatMappedEffInternal[P, V](private val eff: Eff[P], private val f: P => Eff[V]) extends FlatMappedEff[V] {
    type PreviousValueType = P

    def previousEff: Eff[PreviousValueType] = eff

    def fMapFun: PreviousValueType => Eff[V] = f
  }


}


trait Eff[V] {

  def map[R](f: V => R): FlatMappedEff[R] = flatMap(v => Pure(f(v)))

  def flatMap[R](f: V => Eff[R]): FlatMappedEff[R] = FlatMappedEffInternal(this, f)

}