package streams.bidi

import monifu.concurrent.Scheduler
import monifu.reactive.{Observer, Observable}
import streams.bidi.BidiStream.ProcessingAction

/**
  * @author Paweł Sikora
  */
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

}
