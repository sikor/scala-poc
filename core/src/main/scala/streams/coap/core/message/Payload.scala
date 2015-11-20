package streams.coap.core.message

/**
  * @author pawel
  */
case class Payload(value: Array[Byte]) extends AnyVal {
  def toUtf8String: String = new String(value, "UTF-8")

  override def toString = s"Payload(len = ${value.length})"
}

object Payload {
  val empty = Payload(Array.empty[Byte])
}