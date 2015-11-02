import routingmonad.Eff._
import routingmonad.Routing.{OperationWithParams, Route}
import routingmonad.{Eff, Routing}
import shapeless._

/**
  * Created by Paweł Sikora.
  */
object Main {

  val mng = new RouteManager
  val calcPiOp = mng.defineOp("calcPi", calculatePi)

  type EffType[V] = FlatMappedEff[V] :+: Pure[V] :+: Route[V] :+: CNil

  def main(args: Array[String]) {
    println("hello")


    println(mng.executeOp(calcPiOp.withParams(10)))


    val route1 = Routing.route[mng.OpWithParType[Float], Float](calcPiOp.withParams(10))

    val r = Coproduct[Route[Float] :+: CNil](route1).unify

    val computation2 = Coproduct[EffType[Float]](for {
      pi <- Routing.route[mng.OpWithParType[Float], Float](calcPiOp.withParams(10))
      pi10 <- Eff.ret(pi * 10)
    } yield pi + pi10)

    val computation: FlatMappedEff[Float] = for {
      pi <- Routing.route[mng.OpWithParType[Float], Float](calcPiOp.withParams(10))
      pi10 <- Eff.ret(pi * 10)
    } yield pi + pi10

    def resolve[V](comp: Eff[V]): V = {
      comp match {
        case fme: FlatMappedEff[V] => resolve(fme.fMapFun(resolve(fme.previousEff)))
        case p: Pure[V] => p.value
        case r: Route[V] => r.operationWithParams.execute
      }
    }

    println(resolve(computation))


    def resolveCoproduct[V](comp: EffType[V]): V = {
      object resolveCoproductObject extends Poly1 {
        implicit def caseFme = at[FlatMappedEff[V]](fme => resolve(fme.fMapFun(resolve(fme.previousEff))))

        implicit def casePure = at[Pure[V]](_.value)

        implicit def caseRoute = at[Route[V]](r => r.operationWithParams.execute)
      }
      comp.map(resolveCoproductObject).unify
    }

    println(resolveCoproduct(computation2))
  }


  def calculatePi(precision: Int): Float = {
    3.14f
  }

  class RouteManager {

    type Operation[P, R] = Op[P, R]

    type OpWithParType[R] = OpWithPar[_, R]

    case class Op[P, R](name: String, op: P => R) {
      def withParams(p: P): OpWithParType[R] = OpWithPar(this, p)
    }

    case class OpWithPar[P, R](opDef: Op[P, R], p: P) extends OperationWithParams[R] {
      def execute: R = opDef.op(p)
    }

    def defineOp[P, R](name: String, op: P => R): Operation[P, R] = Op(name, op)

    def executeOp[R](opWithPar: OpWithParType[R]): R = opWithPar.execute
  }

}
