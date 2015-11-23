package coap.core.message

/**
  * @author pawel
  */
object MessageCode {
  def fromInt(value: Int) = {
    value match {
      case EmptyMessageCode.value => EmptyMessageCode
      case Get.value => Get
      case Post.value => Post
      case Put.value => Put
      case Delete.value => Delete
      case Created.value => Created
      case Deleted.value => Deleted
      case Valid.value => Valid
      case Changed.value => Changed
      case Content.value => Content
      case Continue.value => Continue
    }
  }
}

sealed trait MessageCode {
  def value: Int
}

sealed abstract class RequestCode(override val value: Int) extends MessageCode

case object Get extends RequestCode(1)

case object Post extends RequestCode(2)

case object Put extends RequestCode(3)

case object Delete extends RequestCode(4)

sealed trait ResponseOrEmptyCode extends MessageCode

sealed abstract class ResponseCode(override val value: Int) extends MessageCode with ResponseOrEmptyCode

case object Created extends ResponseCode(65)

case object Deleted extends ResponseCode(66)

case object Valid extends ResponseCode(67)

case object Changed extends ResponseCode(68)

case object Content extends ResponseCode(69)

case object Continue extends ResponseCode(95)

case object EmptyMessageCode extends MessageCode with ResponseOrEmptyCode {
  val value = 0
}