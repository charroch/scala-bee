import akka.actor.{Actor, ActorRef}
import akka.io._
import akka.util.ByteString
import com.github.jodersky.flow.Serial.Command
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


class XBeeProtocol extends SymmetricPipelineStage[PipelineContext, AT, ByteString] {

  override def apply(ctx: PipelineContext) = new SymmetricPipePair[AT, ByteString] {
    var buffer = None: Option[ByteString]


    def commandPipeline = {
      command: AT =>
        def hex2bytes(hex: String): Array[Byte] = {
          hex.replaceAll("[^0-9A-Fa-f]", "").sliding(2, 2).toArray.map(Integer.parseInt(_, 16).toByte)
        }
        val bb = ByteString(hex2bytes("7E 00 04 08 01 4E 44 64"))
        ctx.singleCommand(bb)
    }

    def eventPipeline = {
      bs: ByteString ⇒
        val data = if (buffer.isEmpty) bs else buffer.get ++ bs
        val (nb, frames) = extractFrames(data, Nil)
        buffer = nb
        /*
       * please note the specialized (optimized) facility for emitting
       * just a single event
       */
        frames match {
          case Nil ⇒ Nil
          case one :: Nil ⇒ ctx.singleEvent(one)
          case many ⇒ many reverseMap (Left(_))
        }
    }
  }

}

class XBeeProcessor(cmds: ActorRef, evts: ActorRef) extends Actor {

  val ctx = new HasActorContext {
    def getContext = XBeeProcessor.this.context
  }

  val pipeline = PipelineFactory.buildWithSinkFunctions(ctx,
    new MessageStage >> new LengthFieldFrame(10000)
  )(cmd ⇒ cmds ! cmd.get,
      evt ⇒ evts ! evt.get)

  def receive = {
    case m: Message ⇒ pipeline.injectCommand(m)
    case b: ByteString ⇒ pipeline.injectEvent(b)
    case t: TickGenerator.Trigger ⇒ pipeline.managementCommand(t)
  }
}