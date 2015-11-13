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

  sealed trait AtomicUpdate

  /**
    * Means that flow should not be repeated and fresh state should be modified using given function.
    */
  case class ChangeStateAndDoAction(stateMod: State => State, effect: () => Future[Ack]) extends AtomicUpdate

  /**
    * Means that flow which constructed this effect should be repeated if expected state does not match
    */
  case class NewStateAndEffect(newState: State, effect: () => Future[Ack]) extends AtomicUpdate

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


    @tailrec
    final override def onNext(msg: Msg): Future[Ack] = {
      val seenState = stateRef.get
      //if producer is let in into onNext message it means that following should hold:
      //there is no buffered message
      //both subscribers have no pending messages
      //one of subscribers can consume message


      val res = seenState.lock match {
        case Unlocked => ChangeStateAndDoAction(s => s.copy(lock = Locked), ifUnlocked(msg))
        case Locked => val promise = Promise[Ack]()
          ChangeStateAndDoAction(s => stateLens.deferAction(s, msg, promise), () => promise.future)
      }
      if (!compareAndSet(seenState, res.stateMod(seenState))) {
        onNext(msg)
      } else {
        res.effect()
      }
    }


    private def ifUnlocked(msg: Msg)(): Future[Ack] = {
      val ackFut = try {
        val result = processingFunction(msg)
        processResult(stateRef.get(), result)
      } catch {
        case NonFatal(e) =>
          onConsumerFinished(e)
          Future.failed(e)
      }
      unlockState(stateRef.get())
      ackFut
    }

  }

  /**
    * Access to this method is synchronized using [[State.lock]].
    * It can modify the state of the subscriber.
    * The state of the subscriber can be also modified when we get onAck callback.
    * But if inside this method we see that subscriber is in WaitingForMsg state it means that it won't send onAck callback.
    * In above situation we have full synchronization on subscriber state until we send him the message and register onComplete callback.
    * If we send message to subscriber and it will answer us immediately with Continue than we don't have to modify state because
    * it would be changed from WaitingForMessage -> WaitingForMessage.
    */
  @tailrec
  private def processResult(state: State, action: ProcessingAction): Future[Ack] = {
    val res = action match {
      case PushToInput(msg) => pushMsg(state, msg, InputLens)
      case PushToOutput(msg) => pushMsg(state, msg, OutputLens)
      case NoAction => NewStateAndEffect(state, () => Continue)
    }
    res match {
      case NewStateAndEffect(newState, effect) =>
        if (!compareAndSet(state, newState)) {
          processResult(stateRef.get, action)
        } else {
          effect()
        }
      case ch: ChangeStateAndDoAction => runChangeState(state, ch)
    }
  }

  private def pushMsg(s: State, msg: Msg, lens: StateLens): AtomicUpdate = {
    lens.getSubscriberState(s) match {
      case WaitingForMsg => pushToSubscriber(msg, lens.getSubscriber(s), lens)
      case WaitingForAck => val promise = Promise[Ack]
        NewStateAndEffect(lens.setSubscriberState(s, PendingMessage(msg, promise)), () => promise.future)
      case m: PendingMessage => NewStateAndEffect(s, () => {
        val err = new IllegalStateException("got next message before earlier was processed")
        onConsumerFinished(err)
        Future.failed(err)
      })
      case Inactive => NewStateAndEffect(s, () => {
        val err = new IllegalStateException("Subscriber is inactive")
        onConsumerFinished(err)
        Future.failed(err)
      })
    }
  }

  /**
    * When we call this method subscriber has to be in WaitingForMessage state.
    * It means that nobody but us can change subscriber state.
    * Thus we can send the message and modify the state afterwards.
    * We don't have to change the state from WaitingForMessage to WaitingForAck before sending the message, we can do it later.
    * State has to be changed before registering onComplete callback on subscriber future and before unlocking/sending buffered message.
    * The only state element we rely on in this method is subscriber state which won't be changed concurrently (see above).
    * Thus our state change is unrelated to other state changes and we just return the function which modify the state.
    */
  private def pushToSubscriber(msg: Msg, sub: Subscriber[Msg], subStateLens: StateLens): ChangeStateAndDoAction = {
    val subAck: Future[Ack] = sub.onNext(msg)
    if (subAck == Continue) {
      ChangeStateAndDoAction(s => subStateLens.setSubscriberState(s, WaitingForMsg), () => Continue)
    } else if (subAck.isCompleted) {
      subAck.value.get match {
        case Success(a) => a match {
          case Continue => ChangeStateAndDoAction(s => subStateLens.setSubscriberState(s, WaitingForMsg), () => Continue)
          case Cancel => ChangeStateAndDoAction(s => subStateLens.setSubscriberState(s, Inactive), () => {
            onConsumerFinished() //we are sending cancel to our producer so it is finished.
            Cancel
          })
        }
        case Failure(t) => ChangeStateAndDoAction(s => subStateLens.setSubscriberState(s, Inactive), () => {
          //when we complete ack with failure than we should not get any messages from given producer
          onConsumerFinished(t)
          Future.failed(t)
        })
      }
    } else {
      ChangeStateAndDoAction(s => subStateLens.setSubscriberState(s, WaitingForAck), () => {
        val producerPromise: Promise[Ack] = Promise()
        subAck.onComplete(ack => producerPromise.completeWith(onSubscriberAck(stateRef.get(), sub, subStateLens, ack)))(sub.scheduler)
        producerPromise.future
      })

    }
  }


  @tailrec
  private def onSubscriberAck(state: State, sub: Subscriber[Msg], subStateLens: StateLens, ack: Try[Ack]): Future[Ack] = {
    val res: AtomicUpdate = ack match {
      case Success(a) =>
        a match {
          case Continue =>
            subStateLens.getSubscriberState(state) match {
              case PendingMessage(pendingMsg, pendingPromise) => //pending message means that both producers are waiting for acks.
                //State modification is defined in pushToSubscriber method. We assume that before calling this method state is WaitingForMessage
                val res = pushToSubscriber(pendingMsg, sub, subStateLens)
                ChangeStateAndDoAction(res.stateMod, () => {
                  pendingPromise.completeWith(res.effect())
                  Continue
                })
              case WaitingForAck => NewStateAndEffect(subStateLens.setSubscriberState(state, WaitingForMsg), () => Continue)
              case _ => NewStateAndEffect(subStateLens.setSubscriberState(state, Inactive), () => {
                sub.onError(new IllegalStateException())
                Future.failed(new IllegalStateException())
              })
            }
          case Cancel => NewStateAndEffect(subStateLens.setSubscriberState(state, Inactive), () => {
            onConsumerFinished()
            Cancel
          })
        }
      case Failure(t) => NewStateAndEffect(subStateLens.setSubscriberState(state, Inactive), () => {
        //when we complete ack with failure than we should not get any messages from given producer
        onConsumerFinished(t)
        Future.failed(t)
      })
    }
    res match {
      case NewStateAndEffect(newState, effect) =>
        if (!compareAndSet(state, newState)) {
          onSubscriberAck(stateRef.get, sub, subStateLens, ack)
        } else {
          effect()
        }
      case ch: ChangeStateAndDoAction => runChangeState(state, ch)
    }

  }

  @tailrec
  private def runChangeState(state: State, ch: ChangeStateAndDoAction): Future[Ack] = {
    if (!compareAndSet(state, ch.stateMod(state))) {
      runChangeState(stateRef.get(), ch)
    } else {
      ch.effect()
    }
  }

  @tailrec
  private def unlockAfterPendingAction(state: State): Unit = {
    if (!compareAndSet(state, state.copy(lock = Unlocked, action = null))) {
      unlockAfterPendingAction(stateRef.get())
    }
  }

  @tailrec
  private def unlockState(state: State): Unit = {
    if (state.action != null) {
      val pendingActionProducerFut = try {
        state.action match {
          case IncomingInputMessage(msg, _) => processResult(state, onInputMessage(msg))
          case IncomingOutputMessage(msg, _) => processResult(state, onOutputMessage(msg))
        }
      } catch {
        case NonFatal(e) =>
          onConsumerFinished(e)
          Future.failed(e)
      }
      unlockAfterPendingAction(state)
      // only after clearing pending action and unlocking we can send ack to producer which was rejected:
      state.action.ackPromise.completeWith(pendingActionProducerFut)
    } else {
      if (!compareAndSet(state, state.copy(lock = Unlocked))) {
        unlockState(stateRef.get())
      }
    }
  }

}
