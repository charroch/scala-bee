package hubstep

import java.nio.ByteBuffer

abstract class XBeeCodec(val length:Int)

object XBeeCodec {
  def parse(a: Array[Byte]):XBeeCodec = {
    if (a.head != 0x7e) throw new MalformedXBeePacket
    val l = ByteBuffer.wrap( a.slice(1,3)).asShortBuffer().get()
    return new XBeeCodec(l) {}
  }
}

class MalformedXBeePacket extends RuntimeException
