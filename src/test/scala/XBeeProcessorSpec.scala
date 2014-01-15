import akka.actor.{Props, Actor, ActorSystem}
import akka.io.{PipelineContext, PipelinePorts, PipelineFactory}
import akka.util.ByteString
import java.nio.ByteOrder
import org.scalatest.{WordSpecLike, BeforeAndAfterAll, Matchers, FlatSpec}
import akka.testkit.{ImplicitSender, TestKit}
import xbee.{Message, XBeeFrame}

object MySpec {


  class EchoActor extends Actor {


    def receive = {
      case x => sender ! x
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

    val PipelinePorts(cmd, evt, mgmt) = PipelineFactory.buildFunctionTriple(new PipelineContext{}, stages)

    import hubstep.utils.HexUtil._
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
