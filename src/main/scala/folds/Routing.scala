package folds


/**
  * @author Paweł Sikora
  */
object Routing {

  trait OperationWithParams[+E] {
    def execute: E
  }

  final case class Route[+O](operationWithParams: O)

}
