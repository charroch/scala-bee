package xbee

import akka.io._
import akka.util.ByteString
import akka.actor.{Props, Actor, ActorRef}
import com.github.jodersky.flow.Serial.Received
import java.nio.ByteBuffer

class LengthFieldFrame extends SymmetricPipelineStage[PipelineContext, ByteString, ByteString] {
  var buffer = None: Option[ByteString]
  val HEADER_LENGTH = 4
  def extractFrames(bs: ByteString, acc: List[ByteString]): (Option[ByteString], Seq[ByteString]) = {
    def size(a: Byte, b: Byte): Int = a << 8 | b;
    bs.toList match {
      case 0x7e :: Nil => (Some(bs), acc)
      case 0x7e :: rest if rest.size < 3 => (Some(bs), acc)
      case 0x7e :: rest if rest.size < 3 => (Some(bs), acc)
      case 0x7e :: a :: b :: api :: rest if rest.size < size(a, b) => (Some(bs), acc)
      case 0x7e :: s0:: s1 :: api :: rest if rest.size >= size(s0, s1) => {
        val frameLength = size(s0, s1) + HEADER_LENGTH
        val frame = bs.take(frameLength)
        extractFrames(bs.drop(frameLength), acc ++ Some(frame))
      }
      case Nil => (None, acc)
      case _ => extractFrames(bs.drop(1), acc)
    }
  }

  def apply(ctx: PipelineContext) = new SymmetricPipePair[ByteString, ByteString] {
    override def commandPipeline = {
      bs: ByteString ⇒
        ???
    }

    def eventPipeline = {
      bs: ByteString ⇒ {
        val data = if (buffer.isEmpty) bs else buffer.get ++ bs
        val (nb, frames) = extractFrames(data, Nil)
        buffer = nb
        frames match {
          case Nil ⇒ Nil
          case one :: Nil ⇒ ctx.singleEvent(one)
          case many ⇒ many reverseMap (Left(_))
        }
      }
    }
  }
}

class XBeeFrame extends SymmetricPipelineStage[PipelineContext, Response, ByteString] {
  def apply(ctx: PipelineContext) = new SymmetricPipePair[Response, ByteString] {
    def eventPipeline = {
      bs: ByteString ⇒ {
        ctx.singleEvent(XBeePacket2.unapply(bs))
      }
    }

    override def commandPipeline = {
      bs: Response ⇒
        ???
    }

  }
}

sealed trait Response
case class AT(frame: Array[Byte]) extends Response
case class AT_RESPONSE(frame: ByteString) extends Response

//lass XBeePacket(r: Response)
object XBeePacket2 {
  def unapply(
              s: ByteString) = {
    val a = s.toArray
    if (a.head != 0x7e) throw new Error
    val frameSize = ByteBuffer.wrap(a.slice(1, 3)).asShortBuffer().get()
    val apiIdentifier = a(3)
    val frame = a.slice(4, frameSize + 3)
    val checksum = a.last
    val verify = frame.foldLeft(apiIdentifier) { (a, b) =>
      ( b + a).toByte
    } + checksum.toByte
    val t = verify.toByte == 0xFF.toByte

    apiIdentifier match {
      case 0x08 => AT(frame)
    }
  }
}

//class XBeeProcessor(cmds: ActorRef, evts: ActorRef) extends Actor {
//
//  import context._
//
//  val reader = actorOf(Props[Lo])
//
//  val ctx = new HasActorContext {
//    def getContext = XBeeProcessor.this.context
//  }
//
//  val pipeline = PipelineFactory.buildWithSinkFunctions(ctx,
//    new XBeeFrame >> new LengthFieldFrame
//  )(cmd ⇒ reader ! cmd.get,
//      evt ⇒ reader ! evt.get)
//
//  def receive = {
//    //case m: Message ⇒ pipeline.injectCommand(m)
//    case b: ByteString ⇒ pipeline.injectEvent(b)
//    case t: TickGenerator.Trigger ⇒ pipeline.managementCommand(t)
//    case Received(data) => pipeline.injectEvent(data)
//  }
//}
