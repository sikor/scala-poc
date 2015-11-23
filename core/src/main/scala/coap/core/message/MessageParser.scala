package coap.core.message

import org.eclipse.californium.core.network.serialization.DatagramReader
import coap.core.message.CoapMessage.AnyCoapMessage

import scala.collection.immutable.SortedMap

/**
  * @author pawel
  */
object MessageParser {

  import MessageFormat._

  def parse(data: Array[Byte]): AnyCoapMessage = {
    val reader = new DatagramReader(data)
    val version: Int = reader.read(VERSION_BITS)
    if (version != 1) {
      throw new scala.IllegalArgumentException(s"Unsupported coap version: $version")
    }
    val msgType: Int = reader.read(TYPE_BITS)
    val tokenLength = reader.read(TOKEN_LENGTH_BITS)
    val code = reader.read(CODE_BITS)
    val mid = reader.read(MESSAGE_ID_BITS)
    val token = readToken(reader, tokenLength)
    val optionsAndNextByte = readOptions(reader)

    val payload = if (optionsAndNextByte._2 == PAYLOAD_MARKER) {
      if (!reader.bytesAvailable) throw new IllegalArgumentException("Message format error")
      Payload(reader.readBytesLeft)
    } else {
      Payload.empty
    }
    CoapMessage(MessageType.fromInt(msgType), MessageCode.fromInt(code), MessageId(mid), token, optionsAndNextByte._1,
      payload)
  }

  def readOptions(reader: DatagramReader): (Options, Byte) = {
    val builder = SortedMap.newBuilder[Int, CoapOption]
    var currentOption: Int = 0
    var nextByte: Byte = 0
    while (reader.bytesAvailable && nextByte != PAYLOAD_MARKER) {
      nextByte = reader.readNextByte
      if (nextByte != PAYLOAD_MARKER) {
        val optionDeltaNibble: Int = (0xF0 & nextByte) >> 4
        currentOption += readOptionValueFromNibble(optionDeltaNibble, reader)
        val optionLengthNibble: Int = 0x0F & nextByte
        val optionLength: Int = readOptionValueFromNibble(optionLengthNibble, reader)
        val option: CoapOption = CoapOption(currentOption, reader.readBytes(optionLength))
        builder += option.number -> option
      }
    }
    val options = Options(builder.result())
    (options, nextByte)
  }

  def readToken(reader: DatagramReader, tokenlength: Int): Token = {
    if (tokenlength == 0) {
      Token.empty
    } else if (tokenlength < 9) {
      Token(reader.readBytes(tokenlength))
    } else {
      throw new IllegalArgumentException(s"Bod token length: $tokenlength")
    }
  }

  def readOptionValueFromNibble(nibble: Int, reader: DatagramReader): Int = {
    if (nibble <= 12) {
      nibble
    } else if (nibble == 13) {
      reader.read(8) + 13
    } else if (nibble == 14) {
      reader.read(16) + 269
    } else {
      throw new IllegalArgumentException("Unsupported option delta " + nibble)
    }
  }


}
