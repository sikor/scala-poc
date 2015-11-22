package streams.bidi

import monifu.reactive.Observer

/**
  * We want to be sure that our Observable has sink before we start to receive messages.
  * So we return our Observer only after we'v got subscriber.
  *
  * @tparam I message we consume
  * @tparam O message we produce
  */
trait BidirectionalObserver[-I, +O] {

  def connect(output: Observer[O]): Observer[I]

}