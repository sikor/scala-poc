package streams.bidi

import streams.bidi.BidiStream.ProcessingAction

/**
  * @author Paweł Sikora
  */
trait BidiProcessor[IC, IP, OC, OP] {

  def onInputMessage(inputMsg: IC): ProcessingAction[IP, OP]

  def onOutputMessage(outputMsg: OC): ProcessingAction[IP, OP]
}
