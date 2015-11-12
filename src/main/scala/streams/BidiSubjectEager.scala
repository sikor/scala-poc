package streams

import monifu.concurrent.atomic.Atomic
import monifu.reactive.Ack.{Continue, Cancel}
import monifu.reactive.{Ack, Subject, Subscriber}
import streams.BidiSubjectEager._

import scala.annotation.tailrec
import scala.concurrent.{Future, Promise}
import scala.util.{Try, Success, Failure}
import scala.concurrent.ExecutionContext.Implicits.global

/**
  * Created by Paweł Sikora.
  */

object BidiSubjectEager {

  /**
    * Api for processing functions
    */
  sealed trait ProcessingAction

  case class PushToInput(msg: Any) extends ProcessingAction

  case class PushToOutput(msg: Any) extends ProcessingAction

  case object NoAction extends ProcessingAction

  /**
    * State of subscriber
    */
  sealed trait SubscriberState

  case class PendingMessages(msg1: PendingMessage, msg2: PendingMessage) extends SubscriberState

  case class PendingMessage(msg: Any, ackPromise: Promise[Ack]) extends SubscriberState

  case object NoMsgAndNoAck extends SubscriberState

  case object WaitingForMsg extends SubscriberState

  case object NotPresent extends SubscriberState

  /**
    * State of consumer
    */
  sealed trait Lock

  case object Locked extends Lock

  case object Unlocked extends Lock

  sealed trait DeferredAction

  case class IncomingInputMessage(msg: Any, ackPromise: Promise[Ack]) extends DeferredAction

  case class IncomingOutputMessage(msg: Any, ackPromise: Promise[Ack]) extends DeferredAction

  /**
    * Complete BiDi State
    */
  case class State(inputSub: SubscriberState, outputSub: SubscriberState, lock: Lock, actions: List[DeferredAction])

}

class BidiSubjectEager(onInputMessage: Any => ProcessingAction) {
  type Msg = Any


  val stateRef = Atomic(State(NotPresent, NotPresent, Unlocked, List()))

  var inputSub: Subscriber[Msg] = _

  val input = new InputSubject

  class InputSubject extends Subject[Msg, Msg] {

    @tailrec
    final override def onSubscribe(subscriber: Subscriber[Msg]): Unit = {
      val seenState = stateRef.get
      if (seenState.inputSub != NotPresent) {
        throw new IllegalArgumentException
      }
      if (!stateRef.compareAndSet(seenState, seenState.copy(inputSub = WaitingForMsg))) {
        onSubscribe(subscriber)
      } else {
        inputSub = subscriber
      }
    }

    override def onError(ex: Throwable): Unit = {}

    override def onComplete(): Unit = {}

    @tailrec
    final override def onNext(elem: Msg): Future[Ack] = {
      val seenState = stateRef.get
      val promise = Promise[Ack]()
      val newState = seenState.lock match {
        case Locked => seenState.copy(actions = IncomingInputMessage(elem, promise) :: seenState.actions)
        case Unlocked => seenState.copy(lock = Locked)
      }
      if (!stateRef.compareAndSet(seenState, newState)) {
        onNext(elem)
      } else {
        seenState.lock match {
          case Locked => promise.future
          case Unlocked => val ack = processResult(onInputMessage(elem))

            ack
        }
      }
    }

    def runPendingActions(): Unit = {
      val seenState = stateRef.get
    }

    def pushToSubscriber(elem: Msg): Unit = {
      val next: Future[Ack] = inputSub.onNext(elem)
      next.onComplete(onSubscriberAck)
    }

    @tailrec
    final def onSubscriberAck(ack: Try[Ack]): Unit = {
      case class Res(newState: State, effect: () => Unit)
      ack match {
        case Failure(t) => t.printStackTrace()
        case Success(a) => val seenState = stateRef.get
          val res: Res = a match {
            case Continue =>
              seenState.inputSub match {
                case PendingMessages(m1, m2) => Res(seenState.copy(inputSub = m2), () => {
                  pushToSubscriber(m1.msg)
                  m1.ackPromise.success(Continue)
                })
                case m: PendingMessage => Res(seenState.copy(inputSub = NoMsgAndNoAck), () => {
                  pushToSubscriber(m.msg)
                  m.ackPromise.success(Continue)
                })
                case NoMsgAndNoAck => Res(seenState.copy(inputSub = WaitingForMsg), () => ())
                case WaitingForMsg => Res(seenState, () => ()) //illegal state
                case NotPresent => Res(seenState, () => ()) //illegal state
              }
            case Cancel => Res(seenState.copy(inputSub = NotPresent), () => ())
          }
          if (!stateRef.compareAndSet(seenState, res.newState)) {
            onSubscriberAck(ack)
          } else {
            res.effect()
          }
      }
    }
  }

  @tailrec
  private def processResult(action: ProcessingAction): Future[Ack] = {
    val seenState = stateRef.get
    val promise = Promise[Ack]
    case class Res(newState: State, effect: () => Unit, result: Future[Ack])
    val res = action match {
      case PushToInput(msg) => seenState.inputSub match {
        case PendingMessages(m1, m2) => Res(seenState, () => (), Cancel) // illegal state got next message before earlier was processed
        case m: PendingMessage =>
          Res(seenState.copy(inputSub = PendingMessages(m, PendingMessage(msg, promise))), () => (), promise.future)
        case NoMsgAndNoAck => Res(seenState.copy(inputSub = PendingMessage(msg, promise)), () => (), promise.future)
        case WaitingForMsg => Res(seenState.copy(inputSub = NoMsgAndNoAck), () => inputSub.onNext(msg), Continue)
        case NotPresent => Res(seenState, () => (), Continue) // drop
      }
      case PushToOutput(msg) => seenState.outputSub match {
        case PendingMessages(m1, m2) => Res(seenState, () => (), Cancel) // illegal state got next message before earlier was processed
        case m: PendingMessage =>
          Res(seenState.copy(outputSub = PendingMessages(m, PendingMessage(msg, promise))), () => (), promise.future)
        case NoMsgAndNoAck => Res(seenState.copy(outputSub = PendingMessage(msg, promise)), () => (), promise.future)
        case WaitingForMsg => Res(seenState.copy(outputSub = NoMsgAndNoAck), () => outputSub.onNext(msg), Continue)
        case NotPresent => Res(seenState, () => (), Continue) // drop
      }
      case NoAction => Res(seenState, () => (), Continue)
    }
    if (!stateRef.compareAndSet(seenState, res.newState)) {
      processResult(action)
    } else {
      res.effect()
      res.result
    }
  }

  var outputSub: Subscriber[Msg] = _

  val output = new Subject[Msg, Msg] {


    override def onSubscribe(subscriber: Subscriber[Msg]): Unit = {
      if (outputSub != null) {
        throw new IllegalArgumentException
      }
      outputSub = subscriber
    }

    override def onError(ex: Throwable): Unit = {}

    override def onComplete(): Unit = {}

    override def onNext(elem: Msg): Future[Ack] = {
      val promise: Promise[Ack] = Promise()

      promise.future
    }
  }
}
