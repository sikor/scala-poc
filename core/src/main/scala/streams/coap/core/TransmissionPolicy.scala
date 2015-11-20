package streams.coap.core

import java.util.concurrent.TimeUnit

import scala.concurrent.duration.{Duration, FiniteDuration}
import scala.util.Random

/**
  * @author pawel
  */

sealed trait TransmissionState

case object InitialState extends TransmissionState

private case class Transmitted(times: Int, lastTimeout: FiniteDuration) extends TransmissionState

sealed trait SendAction

case object Cancel extends SendAction

case class SendWithTimeout(timeout: FiniteDuration) extends SendAction

case class PolicyResult(action: SendAction, newState: TransmissionState)

sealed trait TransmissionPolicy {
  val initialState = InitialState

  def nextAction(state: TransmissionState): PolicyResult
}

object TransmissionPolicy {

  def confirmableMessagePolicy(config: TransmissionParameters): TransmissionPolicy = {
    val random = new Random()
    val maxOffsetMillis: Int = ((config.ackTimeout.toMillis * config.ackRandomFactor).toLong - config.ackTimeout.toMillis).toInt
    val offset = random.nextInt(maxOffsetMillis)
    val timeout = config.ackTimeout + FiniteDuration(offset, TimeUnit.MILLISECONDS)
    RetransmitPolicy(config.maxRetransmit, timeout)
  }

  def nonReliableMessagePolicy(config: TransmissionParameters): TransmissionPolicy = {
    SendOncePolicy(config.maxRtt)
  }
}

case class RetransmitPolicy(maxTimes: Int, initialTimeout: FiniteDuration) extends TransmissionPolicy {
  override def nextAction(state: TransmissionState): PolicyResult = {
    state match {
      case InitialState => PolicyResult(SendWithTimeout(initialTimeout), Transmitted(1, initialTimeout))
      case s: Transmitted =>
        if (s.times < maxTimes) {
          val newTimeout = s.lastTimeout * 2
          PolicyResult(SendWithTimeout(newTimeout), Transmitted(s.times + 1, newTimeout))
        } else {
          PolicyResult(Cancel, s)
        }
    }
  }
}

case class SendOncePolicy(timeout: FiniteDuration) extends TransmissionPolicy {
  override def nextAction(state: TransmissionState): PolicyResult = state match {
    case InitialState => PolicyResult(SendWithTimeout(timeout), Transmitted(1, Duration.Zero))
    case s => PolicyResult(Cancel, s)
  }
}
