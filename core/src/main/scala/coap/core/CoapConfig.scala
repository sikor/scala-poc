package coap.core

import java.util.concurrent.TimeUnit

import scala.concurrent.duration.FiniteDuration

/**
  * @author pawel
  */

/**
  * Default values taken from rfc 7252 chapter 4.8
  *
  * @param ackTimeout minimum timeout for new confirmable messages
  * @param ackRandomFactor the initial timeout is set to a random duration between ackTimeout and (ackTimeout * ackRandomFactor)
  * @param maxRetransmit max number of retransmission of confirmable messages if acknowledge is not received
  * @param nStart maximum number of simultaneous outstanding interactions that we maintain to a given server
  * @param defaultLeisure if we want to answer multicast request than how long to wait before sending the response
  * @param probingRate byte/second, maximum average data rate to take into consideration when resending messages (both non confirmable or confirmable)
  */
case class TransmissionParameters(ackTimeout: FiniteDuration = FiniteDuration(2, TimeUnit.SECONDS), ackRandomFactor: Float = 1.5f,
                                  maxRetransmit: Int = 4, nStart: Int = 1,
                                  defaultLeisure: FiniteDuration = FiniteDuration(5, TimeUnit.SECONDS), probingRate: Int = 1,
                                  maxLatency: FiniteDuration = FiniteDuration(100, TimeUnit.SECONDS)) {
  val processingDelay: FiniteDuration = ackTimeout
  val maxRtt: FiniteDuration = 2 * maxLatency + processingDelay
}

case class CoapConfig(transmissionParameters: TransmissionParameters = TransmissionParameters())
