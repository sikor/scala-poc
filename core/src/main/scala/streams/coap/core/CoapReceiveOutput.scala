package streams.coap.core

/**
  * @author pawel
  */
case class CoapReceiveOutput[+C](envelope: IncomingMessageEnvelope, receiveAction: ReceiveAction[C])
