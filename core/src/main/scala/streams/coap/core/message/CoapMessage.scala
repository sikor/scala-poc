package streams.coap.core.message

/**
  * @author pawel
  */

object CoapMessage {

  object ConfirmableMessage {
    def unapply(coapMessage: AnyCoapMessage): Option[CoapMessage[Confirmable.type, MessageCode]] = {
      if (coapMessage.msgType == Confirmable) {
        Some(coapMessage.asInstanceOf[CoapMessage[Confirmable.type, MessageCode]])
      } else {
        None
      }
    }
  }

  object ConfirmableRequest {
    def unapply(coapMessage: AnyCoapMessage): Option[CoapMessage[Confirmable.type, RequestCode]] = {
      if (coapMessage.msgType == Confirmable && coapMessage.msgCode.isInstanceOf[RequestCode]) {
        Some(coapMessage.asInstanceOf[CoapMessage[Confirmable.type, RequestCode]])
      } else {
        None
      }
    }
  }

  object NonconfirmableRequest {
    def unapply(coapMessage: AnyCoapMessage): Option[CoapMessage[NonConfirmable.type, RequestCode]] = {
      if (coapMessage.msgType == NonConfirmable && coapMessage.msgCode.isInstanceOf[RequestCode]) {
        Some(coapMessage.asInstanceOf[CoapMessage[NonConfirmable.type, RequestCode]])
      } else {
        None
      }
    }
  }

  object IsRequest {
    def unapply(coapMessage: AnyCoapMessage): Option[CoapMessage[RequestMessageType, RequestCode]] = {
      coapMessage.msgType match {
        case _: RequestMessageType if coapMessage.msgCode.isInstanceOf[RequestCode] =>
          Some(coapMessage.asInstanceOf[CoapMessage[RequestMessageType, RequestCode]])
        case _ => None
      }
    }
  }

  object IsResponse {
    def unapply(coapMessage: AnyCoapMessage): Option[CoapMessage[MessageType, ResponseCode]] = {
      if (coapMessage.msgCode.isInstanceOf[ResponseCode]) {
        Some(coapMessage.asInstanceOf[CoapMessage[MessageType, ResponseCode]])
      } else {
        None
      }
    }
  }

  object EmptyMessage {
    def unapply(coapMessage: AnyCoapMessage): Option[CoapMessage[MessageType, EmptyMessageCode.type]] =
      coapMessage.msgType match {
        case Acknowledgement | Reset if coapMessage.msgCode == EmptyMessageCode =>
          Some(coapMessage.asInstanceOf[CoapMessage[MessageType, EmptyMessageCode.type]])
        case _ => None
      }
  }

  object EmptyAck {
    def unapply(coapMessage: AnyCoapMessage): Option[CoapMessage[Acknowledgement.type, EmptyMessageCode.type]] = {
      if (coapMessage.msgType == Acknowledgement && coapMessage.msgCode == EmptyMessageCode) {
        Some(coapMessage.asInstanceOf[CoapMessage[Acknowledgement.type, EmptyMessageCode.type]])
      } else {
        None
      }
    }
  }

  object EmptyRst {
    def unapply(coapMessage: AnyCoapMessage): Option[CoapMessage[Reset.type, EmptyMessageCode.type]] = {
      if (coapMessage.msgType == Reset && coapMessage.msgCode == EmptyMessageCode) {
        Some(coapMessage.asInstanceOf[CoapMessage[Reset.type, EmptyMessageCode.type]])
      } else {
        None
      }
    }
  }

  /**
    * For rejecting messages
    */
  def resetTemplate(msgId: MessageId) = CoapMessage(Reset, EmptyMessageCode, msgId, Token.empty, Options.empty, Payload.empty)

  def acknowledgeTemplate(msgId: MessageId) = CoapMessage(Acknowledgement, EmptyMessageCode, msgId, Token.empty, Options.empty, Payload.empty)

  /**
    * For pinging
    */
  def emptyConfirmableContent(msgId: MessageId, token: Token) = CoapMessage(Confirmable, EmptyMessageCode, msgId, token, Options.empty, Payload.empty)

  type AnyCoapMessage = CoapMessage[MessageType, MessageCode]

}


case class CoapMessage[+T <: MessageType, +C <: MessageCode](msgType: T, msgCode: C, messageId: MessageId, token: Token,
                                                             options: Options, payload: Payload)
