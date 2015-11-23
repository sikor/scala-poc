package coap.core

import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.util.concurrent.atomic.{AtomicInteger, AtomicLong}

import coap.core.message.CoapMessage._
import coap.core.message._

/**
  * Coap problems:
  * - Resend con messages without acks (send method)
  * - Match request with incoming response
  * - Match response with incoming request
  * - Deduplicate incoming requests and responses, match previously sent messages for duplicated incoming messages
  * - Persist observation requests
  * - Receiving acks for confirmable responses
  */
class CoapMessageProcessor[C]
(config: CoapConfig = CoapConfig(), outgoingMessagesStore: ExchangeStore[C] = new ConcurrentHashMapExchangeStore[C]()) {

  var messageId: AtomicInteger = new AtomicInteger(0)

  var tokenId: AtomicLong = new AtomicLong(0)

  def send(envelope: OutgoingEnvelope[C]): CoapSendOutput = {
    envelope match {
      case listenableEnvelope: ListenableEnvelope[C] =>
        envelope.message match {
          case IsRequest(_) => outgoingMessagesStore.storeRequest(listenableEnvelope)
          case _ => outgoingMessagesStore.storeResponseOrEmpty(listenableEnvelope)
        }
      case _ => // We could throw exception here if message is confirmable or request but it would cause only troubles
    }
    val policy = envelope.message match {
      case ConfirmableMessage(_) => TransmissionPolicy.confirmableMessagePolicy(config.transmissionParameters)
      case _ => TransmissionPolicy.nonReliableMessagePolicy(config.transmissionParameters)
    }
    CoapSendOutput(MessageSerializer.serialize(envelope.message), policy)
  }


  def receiveRawData(data: Array[Byte], address: InetSocketAddress): CoapReceiveOutput[C] = {
    val envelope = IncomingMessageEnvelope(MessageParser.parse(data), address)
    CoapReceiveOutput(envelope, receive(envelope))
  }

  /**
    * For response find matching request and stop resending request, returns matched request. (Don't cover observe notifications)
    * For request remember it for deduplication. If response for this request was already sent than find matching response
    *
    * - Response will be matched with request and returned together. If request won't be found than RST will be returned.
    * - For requests the payload and response builder will be returned. The response can be later sent using send method.
    * - Ack and Rst messages will be matched with previously sent message and returned together.
    */
  def receive(envelope: IncomingMessageEnvelope): ReceiveAction[C] = {
    envelope.message match {
      case ConfirmableRequest(m) => ReceiveAction.empty
      case NonconfirmableRequest(m) => ReceiveAction.empty
      case IsResponse(m) => (m.msgType match {
        case Confirmable | NonConfirmable => outgoingMessagesStore.getRequest(envelope.address, m.token)
        case Acknowledgement => outgoingMessagesStore.getRequest(envelope.address, m.token, m.messageId)
        case _ => None
      }).map(r => m.msgType match {
        case Confirmable => ReceiveAction.answerAndMatchedEnvelope(ack(m.messageId, envelope.address), r)
        case _ => ReceiveAction.matchedEnvelope(r)
      }).getOrElse(m.msgType match {
        case Confirmable => ReceiveAction.answer(reset(m.messageId, envelope.address))
        case _ => ReceiveAction.empty
      })
      case EmptyMessage(m) =>
        outgoingMessagesStore.getListenable(envelope.address, m.messageId).map(conEnvelope =>
          m.msgType match {
            case Acknowledgement | Reset => ReceiveAction.matchedEnvelope(conEnvelope)
            case _ => ReceiveAction.empty //message format error
          }
        ).getOrElse(ReceiveAction.empty)
      case ConfirmableMessage(msg) => ReceiveAction.answer(reset(msg.messageId, envelope.address)) //message format error
      case _ => ReceiveAction.empty //Message format error
    }
  }

  def getResponseBuilder(envelope: IncomingMessageEnvelope): ResponseBuilder = {
    ResponseBuilder(envelope)(generateMid)
  }

  def generateMid(inetSocketAddress: InetSocketAddress): MessageId = {
    MessageId(messageId.incrementAndGet() & 0xFFFF)
  }

  def generateTid(inetSocketAddress: InetSocketAddress): Token = {
    val value = tokenId.incrementAndGet()
    val bb = ByteBuffer.allocate(8)
    bb.putLong(value)
    Token(bb.array())
  }

  private def reset(msgId: MessageId, addresss: InetSocketAddress): NonListenableMessageEnvelope = {
    NonListenableMessageEnvelope(CoapMessage.resetTemplate(msgId), addresss)
  }

  private def ack(msgId: MessageId, address: InetSocketAddress): NonListenableMessageEnvelope = {
    NonListenableMessageEnvelope(CoapMessage.acknowledgeTemplate(msgId), address)
  }
}
