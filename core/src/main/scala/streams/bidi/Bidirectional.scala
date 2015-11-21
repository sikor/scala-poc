package streams.bidi

import monifu.reactive.{Subscriber, Observable}

/**
  * Created by Paweł Sikora.
  */
trait Bidirectional[I, O] extends Observable[I] {

  def onSubscribe(subscriber: Subscriber[I], observable: Observable[O]): Unit


  def onSubscribe(subscriber: Subscriber[I]): Unit = onSubscribe(subscriber, Observable.empty)

}
