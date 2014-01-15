package xbee

import akka.io._
import akka.util.ByteString
import akka.actor.{Props, Actor, ActorRef}
import com.github.jodersky.flow.Serial.Received

class Message(val byte: Array[Byte])

class LengthFieldFrame extends SymmetricPipelineStage[PipelineContext, ByteString, ByteString] {

  def apply(ctx: PipelineContext) = new SymmetricPipePair[ByteString, ByteString] {

    var buffer = None: Option[ByteString]

    override def commandPipeline = {
      bs: ByteString ⇒
        ???
    }

    def extractFrames(bs: ByteString, acc: List[ByteString]): (Option[ByteString], Seq[ByteString]) = {
      (None, Seq(ByteString.empty))
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

class XBeeFrame extends SymmetricPipelineStage[PipelineContext, Message, ByteString] {
  def apply(ctx: PipelineContext) = new SymmetricPipePair[Message, ByteString] {
    def eventPipeline = {
      bs: ByteString ⇒ {
        //???
        import hubstep.utils.HexUtil._
        ctx.singleEvent(new Message("0101FFFF01".hex))
      }
    }

    override def commandPipeline = {
      bs: Message ⇒
        ???
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
