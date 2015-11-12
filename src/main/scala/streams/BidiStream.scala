package streams

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicReference

import monifu.concurrent.atomic.Atomic
import monifu.reactive.Ack.{Cancel, Continue}
import monifu.reactive.exceptions.CompositeException
import monifu.reactive.{Ack, Subject, Subscriber}
import streams.BidiStream._

import scala.annotation.tailrec
import scala.concurrent.{Future, Promise}
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}
import scala.collection.JavaConversions._

/**
  * Created by Paweł Sikora.
  */
private[streams] object BidiStream {

  /**
    * Api for processing functions (onInputMessage and onOutputMessage). They takes incoming messages from one of two
    * Observers and return [[ProcessingAction]] containing new message and information to which Subscriber it should be pushed.
    */
  sealed trait ProcessingAction

  case class PushToInput(msg: Any) extends ProcessingAction

  case class PushToOutput(msg: Any) extends ProcessingAction

  case object NoAction extends ProcessingAction

  /**
    * State of subscriber
    */
  sealed trait SubscriberState

  case class PendingMessage(msg: Any, ackPromise: Promise[Ack]) extends SubscriberState

  case object WaitingForAck extends SubscriberState

  case object WaitingForMsg extends SubscriberState

  case object Inactive extends SubscriberState

  /**
    * State of shared state
    */
  sealed trait Lock

  case object Locked extends Lock

  case object Unlocked extends Lock

  sealed trait DeferredAction {
    def ackPromise: Promise[Ack]
  }

  case class IncomingInputMessage(msg: Any, ackPromise: Promise[Ack]) extends DeferredAction

  case class IncomingOutputMessage(msg: Any, ackPromise: Promise[Ack]) extends DeferredAction

  /**
    *
    * @param inputSubState input subscriber state
    * @param outputSubState output subscriber state
    * @param lock Only producer which set this variable to [[Locked]] can call processing functions.
    * @param action When producer can't call processing function because other producer holds the lock than it puts the
    *               action request into this field.
    * @param inputSub can be set only once.
    * @param outputSub can be set only once.
    */
  case class State(inputSubState: SubscriberState, outputSubState: SubscriberState, lock: Lock, action: DeferredAction,
                   inputSub: Subscriber[Any], outputSub: Subscriber[Any])

  sealed trait StateLens {
    def setSubscriberState(state: State, subscriberState: SubscriberState): State

    def getSubscriberState(state: State): SubscriberState

    def deferAction(state: State, msg: Any, ackPromise: Promise[Ack]): State

    def setSubscriber(state: State, subscriber: Subscriber[Any], subscriberState: SubscriberState): State

    def getSubscriber(state: State): Subscriber[Any]
  }

  case object InputLens extends StateLens {
    override def setSubscriberState(state: State, subscriberState: SubscriberState): State = state.copy(inputSubState = subscriberState)

    override def getSubscriberState(state: State): SubscriberState = state.inputSubState

    override def deferAction(state: State, msg: Any, ackPromise: Promise[Ack]): State = state.copy(action = IncomingInputMessage(msg, ackPromise))

    override def setSubscriber(state: State, subscriber: Subscriber[Any], subscriberState: SubscriberState): State =
      state.copy(inputSub = subscriber, inputSubState = subscriberState)

    override def getSubscriber(state: State): Subscriber[Any] = state.inputSub
  }

  case object OutputLens extends StateLens {
    override def setSubscriberState(state: State, subscriberState: SubscriberState): State = state.copy(outputSubState = subscriberState)

    override def getSubscriberState(state: State): SubscriberState = state.outputSubState

    override def deferAction(state: State, msg: Any, ackPromise: Promise[Ack]): State = state.copy(action = IncomingOutputMessage(msg, ackPromise))

    override def setSubscriber(state: State, subscriber: Subscriber[Any], subscriberState: SubscriberState): State =
      state.copy(outputSub = subscriber, outputSubState = subscriberState)

    override def getSubscriber(state: State): Subscriber[Any] = state.outputSub
  }

  case class ChangeStateAndDoAction(newState: State, effect: () => Future[Ack])

}

class BidiStream(onInputMessage: Any => ProcessingAction, onOutputMessage: Any => ProcessingAction) {
  type Msg = Any


  private[this] val stateRef = new AtomicReference[State](State(Inactive, Inactive, Unlocked, null, null, null))
  private[this] val completedCount = Atomic(0)
  private[this] val errors = new ConcurrentLinkedQueue[Throwable]()

  private[this] val input = new EntangledSubject(onInputMessage, InputLens)
  private[this] val output = new EntangledSubject(onOutputMessage, OutputLens)

  def in(): Subject[Msg, Msg] = input

  def out(): Subject[Msg, Msg] = output

  private def compareAndSet(expectedState: State, newState: State): Boolean = {
    val curState: State = stateRef.get()
    (curState eq expectedState) && stateRef.compareAndSet(expectedState, newState)
  }

  private def onConsumerFinished(e: Throwable = null): Unit = {
    if (e != null) {
      errors.add(e)
    }
    // we won't get any new messages so complete our consumers (subscribers)
    if (completedCount.incrementAndGet() == 2) {
      val state = stateRef.get
      completeSubscriber(state, InputLens)
      completeSubscriber(state, OutputLens)
    }
  }

  private def completeSubscriber(state: State, lens: StateLens): Unit = {
    if (lens.getSubscriberState(state) != Inactive) {
      val sub = lens.getSubscriber(state)
      if (errors.size() == 0) {
        sub.onComplete()
      } else if (errors.size() == 1) {
        sub.onError(errors.peek())
      } else if (errors.size() > 1) {
        // We can't get more than 2 errors in total
        // because if we get error in onNext processing it means that one of consumers was waiting for message and
        // if we get error from consumer Future[Ack] it means that one of producer won't give us next message
        sub.onError(new CompositeException(errors.toSeq))
      }
    }
  }

  class EntangledSubject(val processingFunction: Msg => ProcessingAction, val stateLens: StateLens) extends Subject[Msg, Msg] {

    @tailrec
    final override def onSubscribe(subscriber: Subscriber[Msg]): Unit = {
      val seenState = stateRef.get
      if (stateLens.getSubscriber(seenState) != null) {
        subscriber.onError(new IllegalStateException("Cannot subscribe twice to a one subject of BidiStream"))
      } else {
        if (!compareAndSet(seenState, stateLens.setSubscriber(seenState, subscriber, WaitingForMsg))) {
          onSubscribe(subscriber)
        }
      }
    }

    override def onError(ex: Throwable): Unit = {
      onConsumerFinished(ex)
    }

    override def onComplete(): Unit = {
      onConsumerFinished()
    }


    private def ifUnlocked(msg: Msg)(): Future[Ack] = {
      val ackFut = try {
        val result = processingFunction(msg)
        processResult(result)
      } catch {
        case NonFatal(e) =>
          onConsumerFinished(e)
          Future.failed(e)
      }
      unlockState()
      ackFut
    }

    @tailrec
    final override def onNext(msg: Msg): Future[Ack] = {
      val seenState = stateRef.get
      //if producer is let in into onNext message it means that following should hold:
      //there is no pending action
      //both subscribers have no pending messages
      //one of subscribers can consume message


      val res = seenState.lock match {
        case Unlocked => ChangeStateAndDoAction(seenState.copy(lock = Locked), ifUnlocked(msg))
        case Locked => val promise = Promise[Ack]()
          ChangeStateAndDoAction(stateLens.deferAction(seenState, msg, promise), () => promise.future)
      }
      if (!compareAndSet(seenState, res.newState)) {
        onNext(msg)
      } else {
        res.effect()
      }
    }

  }

  private def pushMsg(seenState: State, msg: Msg, lens: StateLens): ChangeStateAndDoAction = {
    lens.getSubscriberState(seenState) match {
      case WaitingForMsg => ChangeStateAndDoAction(lens.setSubscriberState(seenState, WaitingForAck), () => pushToSubscriber(msg, lens.getSubscriber(seenState), lens))
      case WaitingForAck => val promise = Promise[Ack]
        ChangeStateAndDoAction(lens.setSubscriberState(seenState, PendingMessage(msg, promise)), () => promise.future)
      case m: PendingMessage => ChangeStateAndDoAction(seenState, () => Future.failed(new IllegalStateException("got next message before earlier was processed")))
      case Inactive => ChangeStateAndDoAction(seenState, () => Future.failed(new IllegalStateException("Subscriber is inactive")))
    }
  }

  @tailrec
  private def processResult(action: ProcessingAction): Future[Ack] = {
    val seenState = stateRef.get

    val res: ChangeStateAndDoAction = action match {
      case PushToInput(msg) => pushMsg(seenState, msg, InputLens)
      case PushToOutput(msg) => pushMsg(seenState, msg, OutputLens)
      case NoAction => ChangeStateAndDoAction(seenState, () => Continue)
    }
    if (!compareAndSet(seenState, res.newState)) {
      processResult(action)
    } else {
      res.effect()
    }
  }

  /**
    * When we call this method subscriber has to be in WaitingForMessage state.
    */
  private def pushToSubscriber(msg: Msg, sub: Subscriber[Msg], subStateLens: StateLens): Future[Ack] = {
    val subAck: Future[Ack] = sub.onNext(msg)
    if (subAck == Continue) {
      onContinue(sub, subStateLens)
    } else if (subAck.isCompleted) {
      onSubscriberAck(sub, subStateLens, subAck.value.get)
    } else {
      val producerPromise: Promise[Ack] = Promise()
      subAck.onComplete(ack => producerPromise.completeWith(onSubscriberAck(sub, subStateLens, ack)))(sub.scheduler)
      producerPromise.future
    }
  }

  @tailrec
  private def onContinue(sub: Subscriber[Msg], subStateLens: StateLens): Future[Ack] = {
    val seenState = stateRef.get
    val res = subStateLens.getSubscriberState(seenState) match {
      case WaitingForAck => ChangeStateAndDoAction(subStateLens.setSubscriberState(seenState, WaitingForMsg), () => Continue)
      case m: PendingMessage => ChangeStateAndDoAction(subStateLens.setSubscriberState(seenState, WaitingForAck), () => {
        m.ackPromise.completeWith(pushToSubscriber(m.msg, sub, subStateLens))
        Continue
      })
      case WaitingForMsg => ChangeStateAndDoAction(seenState, () => Future.failed(new IllegalStateException())) //illegal state
      case Inactive => ChangeStateAndDoAction(seenState, () => Future.failed(new IllegalStateException())) //illegal state
    }
    if (!compareAndSet(seenState, res.newState)) {
      onContinue(sub, subStateLens)
    } else {
      res.effect()
    }
  }

  @tailrec
  private def onSubscriberAck(sub: Subscriber[Msg], subStateLens: StateLens, ack: Try[Ack]): Future[Ack] = {
    val seenState = stateRef.get
    val res: ChangeStateAndDoAction = ack match {
      case Success(a) =>
        a match {
          case Continue =>
            subStateLens.getSubscriberState(seenState) match {
              case m: PendingMessage => ChangeStateAndDoAction(subStateLens.setSubscriberState(seenState, WaitingForAck), () => {
                m.ackPromise.completeWith(pushToSubscriber(m.msg, sub, subStateLens))
                Continue
              })
              case WaitingForAck => ChangeStateAndDoAction(subStateLens.setSubscriberState(seenState, WaitingForMsg), () => Continue)
              case _ => ChangeStateAndDoAction(seenState, () => Future.failed(new IllegalStateException())) //illegal state
            }
          case Cancel => ChangeStateAndDoAction(subStateLens.setSubscriberState(seenState, Inactive), () => {
            onConsumerFinished()
            Cancel
          })
        }
      case Failure(t) => ChangeStateAndDoAction(subStateLens.setSubscriberState(seenState, Inactive), () => {
        //when we complete ack with failure than we should not get any messages from given producer
        onConsumerFinished(t)
        Future.failed(t)
      })
    }
    if (!compareAndSet(seenState, res.newState)) {
      onSubscriberAck(sub, subStateLens, ack)
    } else {
      res.effect()
    }
  }

  @tailrec
  private def unlockAfterPendingAction(): Unit = {
    val seenState = stateRef.get
    if (!compareAndSet(seenState, seenState.copy(lock = Unlocked, action = null))) {
      unlockAfterPendingAction()
    }
  }

  @tailrec
  private def unlockState(): Unit = {
    val seenState = stateRef.get
    if (seenState.action != null) {
      val pendingActionProducerFut = try {
        seenState.action match {
          case IncomingInputMessage(msg, _) => processResult(onInputMessage(msg))
          case IncomingOutputMessage(msg, _) => processResult(onOutputMessage(msg))
        }
      } catch {
        case NonFatal(e) =>
          onConsumerFinished(e)
          Future.failed(e)
      }
      unlockAfterPendingAction()
      // only after clearing pending action and unlocking we can send ack to producer which was rejected:
      seenState.action.ackPromise.completeWith(pendingActionProducerFut)
    } else {
      if (!compareAndSet(seenState, seenState.copy(lock = Unlocked))) {
        unlockState()
      }
    }
  }

}
