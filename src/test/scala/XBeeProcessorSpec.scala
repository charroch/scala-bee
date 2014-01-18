import akka.actor.{Props, Actor, ActorSystem}
import akka.io.{PipelineContext, PipelinePorts, PipelineFactory}
import akka.util
import akka.util.ByteString
import java.nio.ByteOrder
import org.scalatest.{WordSpecLike, BeforeAndAfterAll, Matchers, FlatSpec}
import akka.testkit.{ImplicitSender, TestKit}
import xbee.{AT, Response, LengthFieldFrame, XBeeFrame}

import hubstep.utils.HexUtil._

class LengthFieldFrameSpec extends WordSpecLike with Matchers {
  "A LengthFieldFrame" must {

    "start with 0x7e" in {
      val b = ByteString("7E 00 04 08 01 49 44 69 7E 00".hex)
      val (rest, results) = new LengthFieldFrame().extractFrames(b, List.empty)
      assert(results(0) == ByteString("7E 00 04 08 01 49 44 69".hex))
      assert(rest.get == ByteString("7E 00".hex))
    }

    "start with 0x7e with exact length" in {
      val b = ByteString("7E 00 04 08 01 49 44 69".hex)
      val (rest, results) = new LengthFieldFrame().extractFrames(b, List.empty)
      assert(results(0) == ByteString("7E 00 04 08 01 49 44 69".hex))
      assert(rest.isEmpty)
    }

    "don't emit until full with 0x7e" in {
      val b = ByteString("7E 00 04".hex)
      val (rest, results) = new LengthFieldFrame().extractFrames(b, List.empty)
      results should be(Nil)
      assert(rest.get == ByteString("7E 00 04".hex))
    }

    "skip none 0x7e" in {
      val b = ByteString("00 7E 00 04".hex)
      val (rest, results) = new LengthFieldFrame().extractFrames(b, List.empty)
      results should have size 0
      assert(rest.get == ByteString("7E 00 04".hex))
    }

    "have 2 messages" in {
      val b = ByteString("7E 00 01 00 00 7E 00 01 00 00".hex)
      val (rest, results) = new LengthFieldFrame().extractFrames(b, List.empty)
      results should have size 2
    }
  }
}

class XBeeProcessorSpec(_system: ActorSystem) extends TestKit(_system) with ImplicitSender with WordSpecLike with Matchers with BeforeAndAfterAll {

  def this() = this(ActorSystem("MySpec"))

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }

  "An XBeeProcessor" must {

    val stages = new XBeeFrame >> new xbee.LengthFieldFrame
    val PipelinePorts(cmd, evt, mgmt) = PipelineFactory.buildFunctionTriple(new PipelineContext {}, stages)

    "have correct message passed" in {
      val a = "7E 00 04 08 01 49 44 69 7E 00 04 08 01 49 44 69".hex
      val encoded: (Iterable[Response], Iterable[ByteString]) = evt(ByteString(a))
      encoded._1.head match {
        case xbee.AT(a) => {a should be ("01 49 44".hex)}
        case _ => fail
      }
    }

  }

}
