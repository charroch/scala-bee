package hubstep

import java.nio.ByteBuffer
import scala.util.Try

trait XBeePacket

abstract class XBeeCodec(val length: Int)

object XBeeCodec {
  def parse(a: Array[Byte]): XBeeCodec = {
    if (a.head != 0x7e) throw new MalformedXBeePacket
    val l = ByteBuffer.wrap(a.slice(1, 3)).asShortBuffer().get()
    val frame = a.slice(2, l + 2)
    val checksum = a.last
    return new XBeeCodec(l) {}
  }


  def parse1(a: Array[Byte]) = {


    if (a.head != 0x7e) throw new MalformedXBeePacket
    val frameSize = ByteBuffer.wrap(a.slice(1, 3)).asShortBuffer().get()
    val apiIdentifier = a(3)
    val frame = a.slice(4, frameSize + 3)
    val checksum = a.last

    val verify = frame.foldLeft(apiIdentifier) { (a, b) =>
      ( b + a).toByte
    } + checksum.toByte

    val as = 0xff
    val t = verify.toByte == 0xFF.toByte

    val c = new xbee.XBeePacket(apiIdentifier, frameSize,frame, checksum)
    val teee = c.verifyChecksum()
  }

  def asFrame(length: Short, tail: Array[Byte]) = {

  }

  def bytes2short(hi: Byte, low: Byte) = ((hi & 0xFF) << 8) | (low & 0xFF)


  type Frame[T] = Array[Byte] => T

  def t(a: Frame[Int])(b: Array[Byte]): Int = a(b)

  // def frame: Try[XBeeCodec] =
}

class MalformedXBeePacket extends RuntimeException
