package streams.bidi

import _root_.streams.bidi.BidiStream.ProcessingAction
import _root_.streams.bidi.InOut.InOutPair
import monifu.concurrent.Scheduler
import monifu.reactive._
import monifu.reactive.subjects.PublishSubject

import scala.concurrent.Future

/**
  * @author Paweł Sikora
  */

object InOut {

  class InOutPair[P, C](private val in: Observable[P], private val out: Observer[C]) extends InOut[P, C] {
    override def onError(ex: Throwable): Unit = out.onError(ex)

    override def onNext(elem: C): Future[Ack] = out.onNext(elem)

    override def onComplete(): Unit = out.onComplete()

    override def onSubscribe(subscriber: Subscriber[P]): Unit = in.onSubscribe(subscriber)
  }

}

trait InOut[P, C] extends Observable[P] with Observer[C] {

  def mapInOut[P2, C2](mapInput: P => ProcessingAction[P2, C], mapOutput: C2 => ProcessingAction[P2, C])
                      (implicit scheduler: Scheduler): InOut[P2, C2] = {
    val bidi = new BidiStream(mapInput, mapOutput)
    bidi.bindSourceConsumer(this)
    bidi.bindSourceProducer(this)
    bidi
  }

  def mapInOut[P2, C2](processor: BidiProcessor[P, P2, C2, C])(implicit scheduler: Scheduler): InOut[P2, C2] =
    this.mapInOut(processor.onInputMessage _, processor.onOutputMessage _)(scheduler)

  def mergeOut(overflowStrategy: OverflowStrategy)(implicit scheduler: Scheduler): InOut[P, Observable[C]] = {
    val transformation = PublishSubject[Observable[C]]()
    transformation.merge(overflowStrategy).subscribe(this)
    new InOutPair(this, transformation)
  }


}
