package streams.coap.core.message


import org.eclipse.californium.core.network.serialization.DatagramWriter
import streams.coap.core.message.CoapMessage.AnyCoapMessage

/**
  * @author pawel
  */
object MessageSerializer {

  import MessageFormat._

  def serialize(message: AnyCoapMessage): Array[Byte] = {
    val writer = new DatagramWriter()
    writer.write(VERSION, VERSION_BITS)
    writer.write(message.msgType.value, TYPE_BITS)
    writer.write(message.token.length, TOKEN_LENGTH_BITS)
    writer.write(message.msgCode.value, CODE_BITS)
    writer.write(message.messageId.value & 0xFFFF, MESSAGE_ID_BITS)
    writer.writeBytes(message.token.value)
    message.options.sortedMap.foldLeft(0) {
      case (previousNumber, (number, option)) =>
        // write 4-bit option delta
        val optionDelta: Int = number - previousNumber
        val optionDeltaNibble: Int = getOptionNibble(optionDelta)
        writer.write(optionDeltaNibble, OPTION_DELTA_BITS)

        // write 4-bit option length
        val optionLength: Int = option.value.length
        val optionLengthNibble: Int = getOptionNibble(optionLength)
        writer.write(optionLengthNibble, OPTION_LENGTH_BITS)

        // write extended option delta field (0 - 2 bytes)
        if (optionDeltaNibble == 13) {
          writer.write(optionDelta - 13, 8)
        } else if (optionDeltaNibble == 14) {
          writer.write(optionDelta - 269, 16)
        }

        // write extended option length field (0 - 2 bytes)
        if (optionLengthNibble == 13) {
          writer.write(optionLength - 13, 8)
        } else if (optionLengthNibble == 14) {
          writer.write(optionLength - 269, 16)
        }

        // write option value
        writer.writeBytes(option.value)
        number
    }
    val payload: Array[Byte] = message.payload.value
    if (payload != null && payload.length > 0) {
      writer.writeByte(PAYLOAD_MARKER)
      writer.writeBytes(payload)
    }
    writer.toByteArray
  }

  /**
    * Returns the 4-bit option header value.
    *
    * @param optionValue
	 * the option value (delta or length) to be encoded.
    * @return the 4-bit option header value.
    */
  private def getOptionNibble(optionValue: Int): Int = {
    if (optionValue <= 12) {
      optionValue
    } else if (optionValue <= 255 + 13) {
      13
    } else if (optionValue <= 65535 + 269) {
      14
    } else {
      throw new IllegalArgumentException("Unsupported option delta " + optionValue)
    }
  }
}
