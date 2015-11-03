package routingmonad


/**
 * Created by Paweł Sikora.
 */
trait Effects {


  trait Eff[V] {

    def map[R](f: V => R): Eff[R] = flatMap(v => Pure(f(v)))

    def flatMap[R](f: V => Eff[R]): Eff[R] = BindImpl(this, f)

  }

  final case class Pure[V](value: V) extends Eff[V]

  sealed trait Bind[V] extends Eff[V] {
    type PreviousValueType
    val source: Eff[PreviousValueType]
    val f: PreviousValueType => Eff[V]
  }

  private final case class BindImpl[P, V](source: Eff[P], f: P => Eff[V]) extends Bind[V] {
    type PreviousValueType = P
  }

  def pure[V](value: V) = Pure(value)

  def bind[P, V](source: Eff[P], f: P => Eff[V]): Eff[V] = BindImpl(source, f)

}

