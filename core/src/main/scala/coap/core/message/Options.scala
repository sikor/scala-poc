package coap.core.message

import scala.collection.immutable.SortedMap

/**
  * @author pawel
  */
case class Options(sortedMap: SortedMap[Int, CoapOption]) extends AnyVal

case class CoapOption(number: Int, value: Array[Byte])

object Options {
  val empty = Options(SortedMap.empty)
}
