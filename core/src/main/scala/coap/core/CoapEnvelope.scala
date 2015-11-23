package coap.core

import java.net.InetSocketAddress

import coap.core.message.CoapMessage.AnyCoapMessage

/**
  * @author pawel
  */
sealed trait CoapEnvelope {
  def message: AnyCoapMessage

  def address: InetSocketAddress
}

case class IncomingMessageEnvelope(message: AnyCoapMessage, address: InetSocketAddress) extends CoapEnvelope

sealed trait OutgoingEnvelope[+E] extends CoapEnvelope

case class ListenableEnvelope[+E](message: AnyCoapMessage,
                                  address: InetSocketAddress,
                                  exchange: E) extends OutgoingEnvelope[E]

case class NonListenableMessageEnvelope(message: AnyCoapMessage, address: InetSocketAddress) extends OutgoingEnvelope[Nothing]