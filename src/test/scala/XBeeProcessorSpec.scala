import akka.actor.{Props, Actor, ActorSystem}
import akka.io.{PipelineContext, PipelinePorts, PipelineFactory}
import akka.util
import akka.util.ByteString
import java.nio.ByteOrder
import org.scalatest.{WordSpecLike, BeforeAndAfterAll, Matchers, FlatSpec}
import akka.testkit.{ImplicitSender, TestKit}
import xbee.{LengthFieldFrame, Message, XBeeFrame}

import hubstep.utils.HexUtil._

object MySpec {


  class EchoActor extends Actor {


    def receive = {
      case x => sender ! x
    }
  }

}

class LengthFieldFrameSpec extends WordSpecLike with Matchers {
  "A LengthFieldFrame" must {

    "start with 0x7e" in {
      val b = ByteString("7E 00 04 08 01 49 44 69 7E 00".hex)
      val (rest, results) = new LengthFieldFrame().extractFrames(b, List.empty)
      assert(results(0) == ByteString("7E 00 04 08 01 49 44 69".hex))
      assert(rest.get == ByteString("7E 00".hex))
    }

    "don't emit until full with 0x7e" in {
      val b = ByteString("7E 00 04".hex)
      val (rest, results) = new LengthFieldFrame().extractFrames(b, List.empty)
      assert(results.size == 0)
      assert(rest.get == ByteString("7E 00 04".hex))
    }
  }
}

class XBeeProcessorSpec(_system: ActorSystem) extends TestKit(_system) with ImplicitSender with WordSpecLike with Matchers with BeforeAndAfterAll {
  def this() = this(ActorSystem("MySpec"))

  import MySpec._

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }

  "An Echo actor" must {


    val stages = new XBeeFrame >> new xbee.LengthFieldFrame
    val echo = system.actorOf(Props[EchoActor])

    val PipelinePorts(cmd, evt, mgmt) = PipelineFactory.buildFunctionTriple(new PipelineContext {}, stages)

    val a = "70 00 04 08 43 4E 49 1D".hex
    val encoded: (Iterable[Message], Iterable[ByteString]) = evt(ByteString(a))


    "send back messages unchanged" in {
      val echo = system.actorOf(Props[EchoActor])
      echo ! "hello world"
      expectMsg("hello world")
      assert(encoded._1.size === 1)
    }

  }

}
