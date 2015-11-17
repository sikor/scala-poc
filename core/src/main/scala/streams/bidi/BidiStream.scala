package streams.bidi

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicReference

import monifu.concurrent.atomic.Atomic
import monifu.reactive.Ack.{Cancel, Continue}
import monifu.reactive.exceptions.CompositeException
import monifu.reactive.{Ack, Subject, Subscriber}
import streams.bidi.BidiStream.{NoAction, PushToOutput, PushToInput, ProcessingAction}

import scala.annotation.tailrec
import scala.collection.JavaConversions._
import scala.concurrent.{Future, Promise}
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}

/**
  * Created by Paweł Sikora.
  */
object BidiStream {

  /**
    * Api for processing functions (onInputMessage and onOutputMessage). They takes incoming messages from one of two
    * Observers and return [[ProcessingAction]] containing new message and information to which Subscriber it should be pushed.
    */
  sealed trait ProcessingAction[+IP, +OP]

  case class PushToInput[IP](msg: IP) extends ProcessingAction[IP, Nothing]

  case class PushToOutput[OP](msg: OP) extends ProcessingAction[Nothing, OP]

  case object NoAction extends ProcessingAction[Nothing, Nothing]


}

/**
  *
  * Stream which contains two subjects that share theirs subscribers and synchronize their producers.
  * By the producer we mean somebody who call methods on observers in our subjects.
  * Each subject can be subscribed to at most once. If message will be send to Inactive subscription
  * (for example when onSubscribe method was never called) than producer of this message will receive failed future.
  *
  * Subscribers are notified about completion when both producers are completed.
  * It is possible to work with one subscriber (two subscribers) and one producer (two producers) under the condition that every message from the producer
  * will be routed to active subscriber. (In such situation don't forget to call onComplete on subject without
  * producer to notify the active subscriber when producer will finish work).
  *
  * onInputMessage and onOutputMessage are called synchronously and their state modifications will be visible to them.
  * @param onInputMessage will be called for each message from input subject
  * @param onOutputMessage will be called for each message from output subject
  */
class BidiStream[IC, IP, OC, OP](onInputMessage: IC => ProcessingAction[IP, OP],
                                 onOutputMessage: OC => ProcessingAction[IP, OP]) {

  private[this] val stateRef = new AtomicReference[State](State(Inactive, Inactive, isLocked = false, null, null, null))
  private[this] val completedProducersCount = Atomic(0)
  private[this] val errors = new ConcurrentLinkedQueue[Throwable]()

  private[this] val input = new EntangledSubject(onInputMessage, InputLens)
  private[this] val output = new EntangledSubject(onOutputMessage, OutputLens)

  def in(): Subject[IC, IP] = input

  def out(): Subject[OC, OP] = output

  class EntangledSubject[C, P](val processingFunction: C => ProcessingAction[IP, OP], val subjectLens: SubjectLens[C, P]) extends Subject[C, P] {

    @tailrec
    final override def onSubscribe(subscriber: Subscriber[P]): Unit = {
      val seenState = stateRef.get
      if (subjectLens.getSubscriber(seenState) != null) {
        subscriber.onError(new IllegalStateException("Cannot subscribe twice to a one subject of BidiStream"))
      } else {
        if (!compareAndSet(seenState, subjectLens.setSubscriber(seenState, subscriber, WaitingForMsg))) {
          onSubscribe(subscriber)
        }
      }
    }

    override def onError(ex: Throwable): Unit = {
      onProducerFinished(ex)
    }

    override def onComplete(): Unit = {
      onProducerFinished()
    }


    @tailrec
    final override def onNext(msg: C): Future[Ack] = {
      //if producer is let in into onNext message it means that following hold:
      //there is no buffered message
      //both subscribers have no pending messages
      //one of subscribers can consume message

      val seenState = stateRef.get
      val res = if (!seenState.isLocked) {
        val newState = seenState.copy(isLocked = true)
        NewStateAndEffect(newState, () => onNextUnderLock(newState, msg))
      } else {
        val promise = Promise[Ack]()
        NewStateAndEffect(subjectLens.bufferMessage(seenState, msg, promise), () => promise.future)
      }
      if (!compareAndSet(seenState, res.newState)) {
        onNext(msg)
      } else {
        res.effect()
      }
    }

    private def onNextUnderLock(state: State, msg: C): Future[Ack] = {
      val ackFut = try {
        val result = processingFunction(msg)
        processResult(state, result)
      } catch {
        case NonFatal(e) =>
          onProducerFinished(e)
          Future.failed(e)
      }
      unlockState(stateRef.get())
      ackFut
    }

  }

  @tailrec
  private def unlockState(state: State): Unit = {
    if (state.bufferedMessage != null) {
      val bufferedMessageAck = try {
        state.bufferedMessage match {
          case BufferedInputMessage(msg, _) => processResult(state, onInputMessage(msg))
          case BufferedOutputMessage(msg, _) => processResult(state, onOutputMessage(msg))
        }
      } catch {
        case NonFatal(e) =>
          onProducerFinished(e)
          Future.failed(e)
      }
      unlockAfterSendingBufferedMessage(state)
      // only after clearing pending action and unlocking we can send ack to producer which was rejected:
      state.bufferedMessage.ackPromise.completeWith(bufferedMessageAck)
    } else {
      if (!compareAndSet(state, state.copy(isLocked = false))) {
        unlockState(stateRef.get())
      }
    }
  }


  @tailrec
  private def unlockAfterSendingBufferedMessage(state: State): Unit = {
    if (!compareAndSet(state, state.copy(isLocked = false, bufferedMessage = null))) {
      unlockAfterSendingBufferedMessage(stateRef.get())
    }
  }


  /**
    * Access to this method is synchronized using [[State.isLocked]].
    * It can modify the state of the subscriber.
    * The state of the subscriber can be also modified when we get onAck callback.
    * But if inside this method we see that subscriber is in WaitingForMsg state it means that it won't send onAck callback.
    * In above situation we have full synchronization on subscriber state until we send him the message and register onComplete callback.
    * If we send message to subscriber and it will answer us immediately with Continue than we don't have to modify state because
    * it would be changed from WaitingForMessage -> WaitingForMessage.
    */
  @tailrec
  private def processResult(state: State, action: ProcessingAction[IP, OP]): Future[Ack] = {
    val res = action match {
      case PushToInput(msg) => pushMsg(state, msg, InputLens)
      case PushToOutput(msg) => pushMsg(state, msg, OutputLens)
      case NoAction => NewStateAndEffect(state, () => Continue)
    }
    res match {
      //here we can do fast loop optimization if we immediately got Continue from subscriber. In such situation we don't
      //change the state from WaitingForMessage to WaitingForAck. The state just remains WaitingForMessage and we have one
      //compareAndSet operation less.
      case fast: FastLoopOptimization[IP, OP] => Continue
      case NewStateAndEffect(newState, effect) =>
        if (!compareAndSet(state, newState)) {
          processResult(stateRef.get, action)
        } else {
          effect()
        }
      case ch: ChangeStateAndEffect =>
        runChangeState(state, ch)
    }
  }

  private def pushMsg[C, P](s: State, msg: P, lens: SubjectLens[C, P]): AtomicUpdate = {
    lens.getSubscriberState(s) match {
      case WaitingForMsg => pushToSubscriber(msg, lens.getSubscriber(s), lens)
      case WaitingForAck => val promise = Promise[Ack]
        NewStateAndEffect(lens.setSubscriberState(s, PendingMessage(msg, promise)), () => promise.future)
      case m: PendingMessage[P] => NewStateAndEffect(s, () => {
        val err = new IllegalStateException("got next message before earlier was processed")
        onProducerFinished(err)
        Future.failed(err)
      })
      case Inactive => NewStateAndEffect(s, () => {
        val err = new IllegalStateException("Subscriber is inactive")
        onProducerFinished(err)
        Future.failed(err)
      })
    }
  }

  /**
    * When we call this method subscriber has to be in WaitingForMessage state.
    * It means that nobody but us can change subscriber state.
    * Thus we can send the message and modify the state afterwards.
    * We don't have to change the state from WaitingForMessage to WaitingForAck before sending the message, we can do it after
    * receiving the the [[Future[Ack]]]. Thanks to that we can do fastLoop and don't change the state to WaitingForAck but just stay in WaitingForMsg.
    * State has to be changed before registering onComplete callback on subscriber future and before unlocking/sending buffered message.
    * The only state element we rely on in this method is subscriber state which won't be changed concurrently (see above).
    * Thus our state change is unrelated to other state changes and we just return the function which modify the state.
    */
  private def pushToSubscriber[C, P](msg: P, sub: Subscriber[P], subjectLens: SubjectLens[C, P]): ChangeStateAndEffectUpdate = {
    val subAck: Future[Ack] = sub.onNext(msg) //we call subscriber immediately
    if (subAck == Continue) {
      subjectLens.onImmediateContinueUpdate
    } else if (subAck.isCompleted) {
      subAck.value.get match {
        case Success(a) => a match {
          case Continue => ChangeStateAndEffect(s => subjectLens.setSubscriberState(s, WaitingForMsg), () => Continue)
          case Cancel => ChangeStateAndEffect(s => subjectLens.setSubscriberState(s, Inactive), () => {
            onProducerFinished() //we are sending cancel to our producer so it is finished.
            Cancel
          })
        }
        case Failure(t) => ChangeStateAndEffect(s => subjectLens.setSubscriberState(s, Inactive), () => {
          //when we complete ack with failure than we should not get any messages from given producer
          onProducerFinished(t)
          Future.failed(t)
        })
      }
    } else {
      ChangeStateAndEffect(s => subjectLens.setSubscriberState(s, WaitingForAck), () => {
        val producerPromise: Promise[Ack] = Promise()
        subAck.onComplete(ack => producerPromise.completeWith(onSubscriberAck(stateRef.get(), sub, subjectLens, ack)))(sub.scheduler)
        producerPromise.future
      })

    }
  }


  /**
    *
    * Handle asynch, not fast looped response from subscriber. It returns Future which should be propagated to producer which sent the message.
    * This method change the state of subscriber. When this method don't return Continue it means that producer won't give us next message.
    * So this method call onProducerFinish in such situation.
    */
  @tailrec
  private def onSubscriberAck[C, P](state: State, sub: Subscriber[P], subscriberLens: SubjectLens[C, P], ack: Try[Ack]): Future[Ack] = {
    val res: AtomicUpdate = ack match {
      case Success(a) =>
        a match {
          case Continue =>
            subscriberLens.getSubscriberState(state) match {
              case PendingMessage(pendingMsg, pendingPromise) =>
                //Pending message state means that both producers are waiting for acks.
                //So we are owning the state of this subscriber and we can send it message immediately.
                //We use pushToSubscriber method to get correct state modification.
                //We assume that before calling pushToSubscriber method state is WaitingForMessage but we don't change the state explicitly
                val res = pushToSubscriber(pendingMsg, sub, subscriberLens)
                ChangeStateAndEffect(res.stateMod, () => {
                  //Bind promise for producer of pending message.
                  pendingPromise.completeWith(res.effect())
                  //Complete promise for producer for which ack was just received.
                  Continue
                })
              case WaitingForAck => NewStateAndEffect(subscriberLens.setSubscriberState(state, WaitingForMsg), () => Continue)
              case Inactive => NewStateAndEffect(state, () => {
                //Should never happen but do best effort in case our clients broke the contract.
                sub.onComplete()
                Cancel
              })
              case WaitingForMsg => NewStateAndEffect(subscriberLens.setSubscriberState(state, Inactive), () => {
                //Should never happen
                val err = new IllegalStateException(s"Got ack when subscriber was in unexpected state: $WaitingForMsg")
                onProducerFinished(err)
                sub.onError(err)
                Future.failed(err)
              })
            }
          case Cancel => NewStateAndEffect(subscriberLens.setSubscriberState(state, Inactive), () => {
            onProducerFinished()
            Cancel
          })
        }
      case Failure(t) => NewStateAndEffect(subscriberLens.setSubscriberState(state, Inactive), () => {
        onProducerFinished(t)
        Future.failed(t)
      })
    }
    res match {
      case NewStateAndEffect(newState, effect) =>
        if (!compareAndSet(state, newState)) {
          onSubscriberAck(stateRef.get, sub, subscriberLens, ack)
        } else {
          effect()
        }
      case ch: ChangeStateAndEffectUpdate => runChangeState(state, ch)
    }

  }


  private def onProducerFinished(e: Throwable = null): Unit = {
    if (e != null) {
      errors.add(e)
    }

    if (completedProducersCount.incrementAndGet() >= 2) {
      // we won't get any new messages so complete subscribers
      val state = stateRef.get
      completeSubscriber(state, InputLens)
      completeSubscriber(state, OutputLens)
    }
  }

  /**
    * If this subscriber was waiting for Message than notify it about completion. Otherwise set its state to Inactive.
    * If we were waiting for ack from this subscriber than it will be notified about completion in [[onSubscriberAck]]
    * method.
    */
  @tailrec
  private def completeSubscriber[C, P](state: State, lens: SubjectLens[C, P]): Unit = {
    val subscriber = lens.getSubscriber(state)
    val result = lens.getSubscriberState(state) match {
      case WaitingForMsg => NewStateAndEffect(lens.setSubscriberState(state, Inactive), () => {
        notifyAboutCompletion(subscriber)
        Cancel
      })
      case _ =>
        NewStateAndEffect(lens.setSubscriberState(state, Inactive), () => Cancel)
    }
    if (!compareAndSet(state, result.newState)) {
      completeSubscriber(stateRef.get(), lens)
    } else {
      result.effect()
    }
    //ignore result.effect
  }

  private def notifyAboutCompletion[T](sub: Subscriber[T]): Unit = {
    if (errors.size() == 0) {
      sub.onComplete()
    } else if (errors.size() == 1) {
      sub.onError(errors.peek())
    } else if (errors.size() > 1) {
      sub.onError(new CompositeException(errors.toSeq))
    }
  }

  @tailrec
  private def runChangeState(state: State, ch: ChangeStateAndEffectUpdate): Future[Ack] = {
    if (!compareAndSet(state, ch.stateMod(state))) {
      runChangeState(stateRef.get(), ch)
    } else {
      ch.effect()
    }
  }


  private def compareAndSet(expectedState: State, newState: State): Boolean = {
    val curState: State = stateRef.get()
    (curState eq expectedState) && stateRef.compareAndSet(expectedState, newState)
  }

  /**
    * State of subscriber
    */
  sealed trait SubscriberState[+P]

  case class PendingMessage[P](msg: P, ackPromise: Promise[Ack]) extends SubscriberState[P]

  case object WaitingForAck extends SubscriberState[Nothing]

  case object WaitingForMsg extends SubscriberState[Nothing]

  case object Inactive extends SubscriberState[Nothing]


  sealed trait BufferedMessage {
    def ackPromise: Promise[Ack]
  }

  case class BufferedInputMessage(msg: IC, ackPromise: Promise[Ack]) extends BufferedMessage

  case class BufferedOutputMessage(msg: OC, ackPromise: Promise[Ack]) extends BufferedMessage

  /**
    *
    * @param inputSubState input subscriber state
    * @param outputSubState output subscriber state
    * @param isLocked Only producer which set this variable to true can call processing functions.
    * @param bufferedMessage When producer can't call processing function because other producer holds the lock than it puts the
    *                        action request into this field.
    * @param inputSub can be set only once.
    * @param outputSub can be set only once.
    */
  case class State(inputSubState: SubscriberState[IP], outputSubState: SubscriberState[OP], isLocked: Boolean, bufferedMessage: BufferedMessage,
                   inputSub: Subscriber[IP], outputSub: Subscriber[OP])

  /**
    * Trait which allows performing operations on state in means of input or output Subject.
    */
  sealed trait SubjectLens[C, P] {
    type SubscriberStateT = SubscriberState[P]
    type SubscriberT = Subscriber[P]

    def setSubscriberState(state: State, subscriberState: SubscriberStateT): State

    def getSubscriberState(state: State): SubscriberStateT

    def bufferMessage(state: State, msg: C, ackPromise: Promise[Ack]): State

    def setSubscriber(state: State, subscriber: SubscriberT, subscriberState: SubscriberStateT): State

    def getSubscriber(state: State): SubscriberT

    def onImmediateContinueUpdate: FastLoopOptimization[C, P]
  }

  case object InputLens extends SubjectLens[IC, IP] {
    override def setSubscriberState(state: State, subscriberState: SubscriberStateT): State = state.copy(inputSubState = subscriberState)

    override def getSubscriberState(state: State): SubscriberStateT = state.inputSubState

    override def bufferMessage(state: State, msg: IC, ackPromise: Promise[Ack]): State = state.copy(bufferedMessage = BufferedInputMessage(msg, ackPromise))

    override def setSubscriber(state: State, subscriber: Subscriber[IP], subscriberState: SubscriberState[IP]): State =
      state.copy(inputSub = subscriber, inputSubState = subscriberState)

    override def getSubscriber(state: State): Subscriber[IP] = state.inputSub

    override def onImmediateContinueUpdate: FastLoopOptimization[IC, IP] = FastLoopOptimization(InputLens)
  }

  case object OutputLens extends SubjectLens[OC, OP] {
    override def setSubscriberState(state: State, subscriberState: SubscriberState[OP]): State = state.copy(outputSubState = subscriberState)

    override def getSubscriberState(state: State): SubscriberState[OP] = state.outputSubState

    override def bufferMessage(state: State, msg: OC, ackPromise: Promise[Ack]): State = state.copy(bufferedMessage = BufferedOutputMessage(msg, ackPromise))

    override def setSubscriber(state: State, subscriber: Subscriber[OP], subscriberState: SubscriberState[OP]): State =
      state.copy(outputSub = subscriber, outputSubState = subscriberState)

    override def getSubscriber(state: State): Subscriber[OP] = state.outputSub

    override def onImmediateContinueUpdate: FastLoopOptimization[OC, OP] = FastLoopOptimization(OutputLens)
  }


  /**
    * We keep [[State]] inside atomic reference. Updates to it are made using compareAndSet. The updates are described
    * by instances of classes implementing this trait.
    */
  sealed trait AtomicUpdate


  /**
    * Means that if we saw old state and compareAndSet failed than algorithm should not be repeated.
    * (Algorithm could perform side effects)
    * Fresh state should be modified using stateMod function.
    */
  sealed trait ChangeStateAndEffectUpdate extends AtomicUpdate {
    val stateMod: State => State
    val effect: () => Future[Ack]
  }

  case class ChangeStateAndEffect(stateMod: State => State, effect: () => Future[Ack]) extends ChangeStateAndEffectUpdate

  case class FastLoopOptimization[P, C](lens: SubjectLens[P, C]) extends ChangeStateAndEffectUpdate {
    override val stateMod: (State) => State = s => lens.setSubscriberState(s, WaitingForMsg)
    override val effect: () => Future[Ack] = () => Continue
  }

  /**
    * Means that algorithm which constructed this effect should be repeated if expected state does not match
    */
  case class NewStateAndEffect(newState: State, effect: () => Future[Ack]) extends AtomicUpdate


}
