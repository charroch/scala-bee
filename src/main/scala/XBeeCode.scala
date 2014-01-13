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
    val head = a.head
    val size = ByteBuffer.wrap(a.slice(1, 3)).asShortBuffer().get()

    if (a.head != 0x7e) throw new MalformedXBeePacket
    val l = ByteBuffer.wrap(a.slice(1, 3)).asShortBuffer().get()
    val apiIdentifier = a(3)
    val frame = a.slice(4, l + 3)
    val checksum1 = a.last
    val checksum = 0xFF - frame.foldRight(0x00) {
      _ + _
    }

    val verify = frame.foldRight(apiIdentifier.toByte) { (a, b) =>
      (a + b).toByte
    } + checksum1


    val as = 0xff
    val t = verify == 0xFF

    val te = 2
  }

  //  def p(s: Stream[Byte]) = {
  //    for (
  //      header:Byte <- s.head;
  //      //frameLength <- Some(s.tail) if (header == 0x7e) { Some(2) } else None
  //
  //    )
  //  }
  //
  //  def a(a: Array[Byte]) = a match {
  //    case 0x7e:: tail => asFrame(bytes2short(tail(0), tail(1)), tail.slice(2, tail.size))
  //  }

  //  def a(a: Array[Byte]) = a match {
  //    case 0x7e :: b :: a => ""
  //    case encoding => "fdwefe"
  //  }

  // def encoding = (a: Array[Byte]) => a match { case 0x7e :: s1 :: s2 :: rest => asFrame((bytes2short(s1, s2), rest)) }

  def asFrame(length: Short, tail: Array[Byte]) = {

  }

  def bytes2short(hi: Byte, low: Byte) = ((hi & 0xFF) << 8) | (low & 0xFF)


  type Frame[T] = Array[Byte] => T

  def t(a: Frame[Int])(b: Array[Byte]): Int = a(b)

  // def frame: Try[XBeeCodec] =
}

class MalformedXBeePacket extends RuntimeException
