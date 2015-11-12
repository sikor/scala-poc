import routingmonad.{Effects, Routing}

import scala.reflect.runtime.universe._

/**
 * Created by Paweł Sikora.
 */
object Main {

  object MyEffects extends Effects with Routing

  import MyEffects._

  val mng = new RouteManager
  val calcPiOp = mng.defineOp("calcPi", calculatePi)

  def main(args: Array[String]) {
    println("hello")


    println(mng.executeOp(calcPiOp.withParams(10)))

    val route1 = route[Float](calcPiOp.withParams(10))

    val computation: Eff[Float] = for {
      pi <- route(calcPiOp.withParams(10))
      pi10 <- pure(pi * 10)
    } yield pi + pi10

    def resolve[V: TypeTag](comp: Eff[V]): V = {
      comp match {
        case fme: Bind[V] => resolve(fme.f(resolve(fme.source)))
        case p: Pure[V] => p.value
        case r: Route[V] => resolve(r.operationWithParams.execute)
      }
    }
    println(resolve(computation))
    folds.Effects.Execute.example()

    println(folds.MyEffects.example())
  }


  def calculatePi(precision: Int): Eff[Float] = {
    pure(3.14f)
  }

  class RouteManager {

    type Operation[P, R] = Op[P, R]

    type OpWithParType[R] = OpWithPar[_, R]

    case class Op[P, R](name: String, op: P => Eff[R]) {
      def withParams(p: P): OpWithParType[R] = OpWithPar(this, p)
    }

    case class OpWithPar[P, R](opDef: Op[P, R], p: P) extends OperationWithParams[R] {
      def execute: Eff[R] = opDef.op(p)
    }

    def defineOp[P, R](name: String, op: P => Eff[R]): Operation[P, R] = Op(name, op)

    def executeOp[R](opWithPar: OpWithParType[R]): Eff[R] = opWithPar.execute
  }

}
