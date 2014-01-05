import akka.actor.{Props, ActorLogging, Actor, ActorRef}
import akka.io._
import akka.util.{ByteStringBuilder, ByteString}
import com.github.jodersky.flow.Serial.{Event, Received, Command}
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


//    class XBeeCommand (
//                        - Start delimiter: 7E
//    - Length: 00 04 (4)
//    - Frame type: 08 (AT Command)
//    - Frame ID: 01 (1)
//    - AT Command: 49 44 (ID)
//    - Checksum: 69
//                        )

    class XBeeEvent extends Event
    /*
    AT Command Response

7E 00 06 88 03 53 44 00 03 DA

    - Start delimiter: 7E
    - Length: 00 06 (6)
    - Frame type: 88 (AT Command Response)
    - Frame ID: 03 (3)
    - AT Command: 53 44 (SD)
    - Status: 00 (Status OK)
    - Response: 03
    - Checksum: DA
     */

    def extractFrames(bf: ByteString): Option[ByteString] = {
      val bs = bf.toByteBuffer
      if (bs.limit() < 1) return None
      println(bs.get)
         // if (bs.get() != 0x7E) return None
      val length = bs.getShort
      println("lenght: " + length)
      val apiIdentifier = bs.get
      println("%02x".format(apiIdentifier))
      val frameIde = bs.get()
      val command1 = bs.get
      val command2 = bs.get
      println(">>>" + "%02x".format(command1))
      println("%02x".format(command2))

      None
    }

    def commandPipeline = {
      command: ByteString =>
        def hex2bytes(hex: String): Array[Byte] = {
          hex.replaceAll("[^0-9A-Fa-f]", "").sliding(2, 2).toArray.map(Integer.parseInt(_, 16).toByte)
        }
        //val bb = ByteString(hex2bytes("7E 00 04 08 01 4E 44 64"))
        val bb = ByteString(hex2bytes("7E 00 04 08 01 49 44 69"))
// 7E 00 04 08 43 4E 49 1D
// 7E 00 0F 88 43 4E 49 00 43 6F 6E 74 72 6F 6C 6C 65 72 79 (15 lenght)
        /**
         *
        - Start delimiter: 7E
    - Length: 00 0F (15)
    - Frame type: 88 (AT Command Response)
    - Frame ID: 43 (67)
    - AT Command: 4E 49 (NI)
    - Status: 00 (Status OK)
    - Response: 43 6F 6E 74 72 6F 6C 6C 65 72 (Controller)
    - Checksum: 79

         */

      /*

    - Start delimiter: 7E
    - Length: 00 04 (4)
    - Frame type: 08 (AT Command)
    - Frame ID: 43 (67)
    - AT Command: 4E 49 (NI)
    - Checksum: 1D
       */
        ctx.singleCommand(bb)
    }

    def eventPipeline = {
      bs: ByteString ⇒ {
        println("in here " + buffer.size + buffer + " " + bs)
        extractFrames(buffer).map(b => {
          println("in here event" )
          ctx.singleEvent(b)
        }).getOrElse(
            {
              buffer = buffer ++ bs
               Nil
            }
          )
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