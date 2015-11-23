package coap.core.message

/**
  * @author pawel
  */
/**
  * CoAP message format.
  */
object MessageFormat {
  /** number of bits used for the encoding of the CoAP version field. */
  val VERSION_BITS: Int = 2
  /** number of bits used for the encoding of the message type field. */
  val TYPE_BITS: Int = 2
  /** number of bits used for the encoding of the token length field. */
  val TOKEN_LENGTH_BITS: Int = 4
  /** number of bits used for the encoding of the request method/response code field. */
  val CODE_BITS: Int = 8
  /** number of bits used for the encoding of the message ID. */
  val MESSAGE_ID_BITS: Int = 16
  /** number of bits used for the encoding of the option delta field. */
  val OPTION_DELTA_BITS: Int = 4
  /** number of bits used for the encoding of the option delta field. */
  val OPTION_LENGTH_BITS: Int = 4
  /** One byte which indicates indicates the end of options and the start of the payload. */
  val PAYLOAD_MARKER: Byte = 0xFF.toByte
  /** CoAP version supported by this Californium version. */
  val VERSION: Int = 1
  /** The code value of an empty message. */
  val EMPTY_CODE: Int = 0
  /** The lowest value of a request code. */
  val REQUEST_CODE_LOWER_BOUND: Int = 1
  /** The highest value of a request code. */
  val REQUEST_CODE_UPPER_BOUNT: Int = 31
  /** The lowest value of a response code. */
  val RESPONSE_CODE_LOWER_BOUND: Int = 64
  /** The highest value of a response code. */
  val RESPONSE_CODE_UPPER_BOUND: Int = 191
}
