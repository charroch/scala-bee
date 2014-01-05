import hubstep.XBeeCodec
import org.scalatest._
import hubstep.utils.HexUtil._

class XBeeCodecSpec extends FlatSpec with Matchers {

  it should "start with a delimiter (0x7e)" in {
    val a = "70 00 04 08 43 4E 49 1D".hex
    intercept[hubstep.MalformedXBeePacket] {
      XBeeCodec.parse(a)
    }
  }

  it should "specify the frame length" in {
    val a = "7e 00 04 08 43 4E 49 1D".hex
    val codec = XBeeCodec.parse(a)
    assert (codec.length === 4)
  }

  it should "have a checksum" in {

  }

}
