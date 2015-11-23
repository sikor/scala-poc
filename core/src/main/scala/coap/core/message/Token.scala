package coap.core.message

import java.util

/**
  * @author pawel
  */
object Token {
  val empty = Token(Array.empty[Byte])
}

case class Token(value: Array[Byte]) {
  def isEmpty = value.isEmpty

  override lazy val hashCode = util.Arrays.hashCode(value)

  def length: Int = value.length

  override def equals(o: Any) = o match {
    case t: Token => util.Arrays.equals(value, t.value)
    case _ => false
  }

  override def toString: String = {
    value.iterator.map(b => (b & 0xFF).toString).mkString(":")
  }
}