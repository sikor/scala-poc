package streams.coap.core

import java.net.InetSocketAddress

import streams.coap.core.message.CoapMessage.{AnyCoapMessage, NonconfirmableRequest, ConfirmableRequest}
import streams.coap.core.message._

/**
  * @author pawel
  */

object ResponseBuilder {

  def apply(request: IncomingMessageEnvelope)(midProvider: (InetSocketAddress) => MessageId): ResponseBuilder = {
    request.message match {
      case ConfirmableRequest(r) => new ConResponseBuilder(r, midProvider, request.address)
      case NonconfirmableRequest(r) => new NonResponseBuilder(r, midProvider, request.address)
      case _ => throw new IllegalArgumentException(s"can't build response for non request message: ${request.message}")
    }
  }
}

sealed trait ResponseBuilder {
  def buildResponse(code: ResponseCode, options: Options, payload: Payload): AnyCoapMessage

  /**
    *
    * Non confirmable messages MAY be rejected by sending reset message.
    */
  def buildRst(): AnyCoapMessage = {
    CoapMessage.resetTemplate(midProvider(address))
  }

  protected def request: CoapMessage[MessageType, RequestCode]

  protected def midProvider: (InetSocketAddress) => MessageId

  protected def address: InetSocketAddress
}

class NonResponseBuilder(override val request: CoapMessage[NonConfirmable.type, RequestCode],
                         override val midProvider: (InetSocketAddress) => MessageId,
                         override val address: InetSocketAddress) extends ResponseBuilder {

  def buildResponse(code: ResponseCode, options: Options, payload: Payload): AnyCoapMessage = {
    CoapMessage(NonConfirmable, code, midProvider(address), request.token, options, payload)
  }
}

class ConResponseBuilder(override val request: CoapMessage[Confirmable.type, RequestCode],
                         override val midProvider: (InetSocketAddress) => MessageId,
                         override val address: InetSocketAddress) extends ResponseBuilder {

  def buildAck(): AnyCoapMessage = {
    CoapMessage.acknowledgeTemplate(request.messageId)
  }

  def buildResponse(code: ResponseCode, options: Options, payload: Payload): AnyCoapMessage = {
    unreliableResponse(Acknowledgement, code, request.messageId, options, payload)
  }

  def conResponse(code: ResponseCode, options: Options, payload: Payload): AnyCoapMessage = {
    CoapMessage(Confirmable, code, midProvider(address), request.token, options, payload)
  }

  def nonResponse(code: ResponseCode, options: Options, payload: Payload): AnyCoapMessage = {
    unreliableResponse(NonConfirmable, code, midProvider(address), options, payload)
  }

  private def unreliableResponse(msgType: UnreliableMessageType, code: ResponseCode, messageId: MessageId,
                                 options: Options, payload: Payload): AnyCoapMessage = {
    CoapMessage(msgType, code, messageId, request.token, options, payload)
  }
}
