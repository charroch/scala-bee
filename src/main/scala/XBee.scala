import akka.actor.{Props, ActorLogging, Actor, ActorRef}
import akka.io._
import akka.util.{ByteStringBuilder, ByteString}
import com.github.jodersky.flow.Serial.{Received, Command}
import scala.annotation.tailrec

object XBee {

  trait XBee

  case class Coordinator(ni: String) extends XBee

  case class EndDevice(ni: String) extends XBee

  case class Router(ni: String) extends XBee

}

object AT {
  type MY = String
  type NI = String
}

trait AT extends Command

case class ND(params: Option[Either[AT.MY, AT.NI]]) extends AT


class XBeeProtocol extends SymmetricPipelineStage[PipelineContext, ByteString, ByteString] {

  override def apply(ctx: PipelineContext) = new SymmetricPipePair[ByteString, ByteString] {
    var buffer:ByteString = ByteString.empty

    def commandPipeline = {
      command: ByteString =>
        def hex2bytes(hex: String): Array[Byte] = {
          hex.replaceAll("[^0-9A-Fa-f]", "").sliding(2, 2).toArray.map(Integer.parseInt(_, 16).toByte)
        }
        val bb = ByteString(hex2bytes("7E 00 04 08 01 4E 44 64"))
        ctx.singleCommand(bb)
    }

    def eventPipeline = {
      bs: ByteString ⇒
        println("in here " + buffer.size)
        if (buffer.size < 5) {
          buffer = buffer.concat(bs)
          Nil
        } else {
          println("in here event" )
          ctx.singleEvent(buffer)
        }
    }
  }
}


class XBeeProcessor(cmds: ActorRef, evts: ActorRef) extends Actor {

  import context._

  val reader = actorOf(Props[Lo])

  val ctx = new HasActorContext {
    def getContext = XBeeProcessor.this.context
  }

  val pipeline = PipelineFactory.buildWithSinkFunctions(ctx,
    new XBeeProtocol
  )(cmd ⇒ reader ! cmd.get,
      evt ⇒ reader ! evt.get)

  def receive = {
    //case m: Message ⇒ pipeline.injectCommand(m)
    case b: ByteString ⇒ pipeline.injectEvent(b)
    case t: TickGenerator.Trigger ⇒ pipeline.managementCommand(t)
    case Received(data) => pipeline.injectEvent(data)
  }
}

class Lo extends Actor with ActorLogging {
  def receive = {
    case a => log.info(a + " M<>>")
  }
}