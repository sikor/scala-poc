package coap.core.message

/**
  * @author pawel
  */

object MessageType {
  def fromInt(value: Int): MessageType = {
    value match {
      case Confirmable.value => Confirmable
      case NonConfirmable.value => NonConfirmable
      case Acknowledgement.value => Acknowledgement
      case Reset.value => Reset
    }
  }
}

sealed trait MessageType {
  def value: Int
}

sealed trait UnreliableMessageType extends MessageType

sealed trait RequestMessageType extends MessageType

case object Confirmable extends MessageType with RequestMessageType {
  val value = 0
}

case object NonConfirmable extends MessageType with UnreliableMessageType with RequestMessageType {
  val value = 1
}

case object Acknowledgement extends MessageType with UnreliableMessageType {
  val value = 2
}

case object Reset extends MessageType with UnreliableMessageType {
  val value = 3
}
