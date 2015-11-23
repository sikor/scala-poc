package coap.core

/**
  * @author pawel
  */

/**
  * Possible high level reactions to incoming messages:
  * - Answer to request using builder. (piggy backed by default)
  * - Call callback on ack/rst/response (send ack for con response by default)
  * can be hidden:
  * - Handle deduplication: resend previous message,
  * - handle ping
  */
case class ReceiveAction[+C] private
(ans: NonListenableMessageEnvelope, cb: ListenableEnvelope[C]) {
  val answer: Option[NonListenableMessageEnvelope] = Option(ans)
  val matchedEnvelope: Option[ListenableEnvelope[C]] = Option(cb)
}

object ReceiveAction {
  val empty = ReceiveAction[Nothing](null, null)

  def answer(ans: NonListenableMessageEnvelope): ReceiveAction[Nothing] = {
    ReceiveAction[Nothing](ans, null)
  }

  def matchedEnvelope[C](cb: ListenableEnvelope[C]): ReceiveAction[C] = {
    ReceiveAction(null, cb)
  }

  def answerAndMatchedEnvelope[C](ans: NonListenableMessageEnvelope, cb: ListenableEnvelope[C]) = {
    ReceiveAction(ans, cb)
  }
}