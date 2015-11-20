package streams.coap.core

import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentHashMap

import streams.coap.core.message.{Token, MessageId}

/**
  * @author pawel
  */
trait ExchangeStore[C] {

  case class AddressMsgIdToken(address: InetSocketAddress, msgId: MessageId, token: Token)

  def storeRequest(envelope: ListenableEnvelope[C]): Unit

  def storeResponseOrEmpty(envelope: ListenableEnvelope[C]): Unit

  def getRequest(address: InetSocketAddress, token: Token): Option[ListenableEnvelope[C]]

  def getRequest(address: InetSocketAddress, token: Token, messageId: MessageId): Option[ListenableEnvelope[C]]

  def getListenable(address: InetSocketAddress, messageId: MessageId): Option[ListenableEnvelope[C]]
}

class ConcurrentHashMapExchangeStore[C] extends ExchangeStore[C] {

  case class AddressMsgId(address: InetSocketAddress, msgId: MessageId)

  case class AddressToken(address: InetSocketAddress, token: Token)

  private val requestStoreByToken: ConcurrentHashMap[AddressToken, ListenableEnvelope[C]] = new ConcurrentHashMap()
  private val callbackStoreByMsgId: ConcurrentHashMap[AddressMsgId, ListenableEnvelope[C]] = new ConcurrentHashMap()

  override def storeRequest(envelope: ListenableEnvelope[C]): Unit = {
    callbackStoreByMsgId.put(AddressMsgId(envelope.address, envelope.message.messageId), envelope)
    requestStoreByToken.put(AddressToken(envelope.address, envelope.message.token), envelope)
  }

  override def storeResponseOrEmpty(envelope: ListenableEnvelope[C]): Unit = {
    callbackStoreByMsgId.put(AddressMsgId(envelope.address, envelope.message.messageId), envelope)
  }

  override def getRequest(address: InetSocketAddress, token: Token): Option[ListenableEnvelope[C]] =
    Option(requestStoreByToken.get(AddressToken(address, token)))

  override def getRequest(address: InetSocketAddress, token: Token, messageId: MessageId): Option[ListenableEnvelope[C]] =
    for {
      byToken <- getRequest(address, token)
      byMid <- getListenable(address, messageId)
      if byToken == byMid
    } yield byToken

  override def getListenable(address: InetSocketAddress, messageId: MessageId): Option[ListenableEnvelope[C]] =
    Option(callbackStoreByMsgId.get(AddressMsgId(address, messageId)))
}