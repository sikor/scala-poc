package streams.bidi

import monifu.reactive.{Observer, Observable}

/**
  * @author Paweł Sikora
  */
trait InOut[I, O] extends Observable[I] with Observer[O] {

}
