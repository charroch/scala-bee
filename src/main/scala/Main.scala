import akka.actor._
import akka.io._
import akka.io.IO
import akka.util.{CompactByteString, ByteString}
import com.github.jodersky.flow.Serial._
import com.github.jodersky.flow.Serial.CommandFailed
import com.github.jodersky.flow.Serial.Open
import com.github.jodersky.flow.Serial.Open
import com.github.jodersky.flow.Serial.Open
import com.github.jodersky.flow.Serial.Opened
import com.github.jodersky.flow.Serial.Opened
import com.github.jodersky.flow.Serial.Opened
import com.github.jodersky.flow.Serial.Received
import com.github.jodersky.flow.Serial.Received
import com.github.jodersky.flow.Serial.Register
import com.github.jodersky.flow.Serial.Register
import com.github.jodersky.flow.Serial.Write
import com.github.jodersky.flow.Serial.Write
import com.github.jodersky.flow.SerialSettings
import com.github.jodersky.flow.SerialSettings
import com.github.jodersky.flow.SerialSettings
import com.github.jodersky.flow.{AccessDeniedException, Serial, SerialSettings}
import java.nio.{ByteOrder, ByteBuffer}
import scala.annotation.tailrec
import scala.collection.immutable.Iterable
import scala.Some
import scala.Some

object Main extends App {
  val settings = SerialSettings(
    port = "/dev/ttyUSB0",
    baud = 9600
  )

  val settings2 = SerialSettings(
    port = "/dev/ttyUSB1",
    baud = 9600
  )
  println("Starting terminal system, enter :q to exit.")
  val system = ActorSystem("flow")
  val terminal = system.actorOf(Controller(settings), name = "terminal")
  //val terminal2 = system.actorOf(Terminal(settings2), name = "terminal2")

  system.registerOnTermination(println("Stopped terminal system."))
}


class Controller(settings: SerialSettings) extends Actor with ActorLogging {

  import Controller._
  import context._

  val reader = actorOf(Props[Lo])
  val reader2 = actorOf(Props(classOf[XBeeProcessor], reader, reader))

  override def preStart() = {
    log.info(s"Requesting manager to open port: ${settings.port}, baud: ${settings.baud}")
    IO(Serial) ! Serial.Open(settings)
  }

  def hex2bytes(hex: String): Array[Byte] = {
    hex.replaceAll("[^0-9A-Fa-f]", "").sliding(2, 2).toArray.map(Integer.parseInt(_, 16).toByte)
  }

  def bytes2hex(bytes: Array[Byte], sep: Option[String] = None): String = {
    sep match {
      case None => bytes.map("%02x".format(_)).mkString
      case _ => bytes.map("%02x".format(_)).mkString(sep.get)
    }
    // bytes.foreach(println)
  }

  def receive = {
    case CommandFailed(cmd: Open, reason: AccessDeniedException) => println("you're not allowed to open that port!")
    case CommandFailed(cmd: Open, reason) => println("could not open port for some other reason: " + reason.getMessage)
    case Opened(settings, op) => {
      val operator = sender
      operator ! Register(reader2)
      operator ! Write(ByteString.apply(hex2bytes("7E 00 04 08 01 4E 44 64")))
      log.info("opened")
    }

    case Received(data) => {
      log.info("got data: " + formatData(data) + " " + data.size + " <-> " + bytes2hex(data.toArray) + (data.head == 0x7E))

      data.foreach(a => log.info(a.toString))
    }
  }
}

object Controller {

  case class Wrote(data: ByteString) extends Event

  def apply(settings: SerialSettings) = Props(classOf[Controller], settings)

  private def formatData(data: ByteString) = data.mkString("[", ",", "]") + " " + (new String(data.toArray, "UTF-8"))
}


class LengthFieldFrame(maxSize: Int,
                       byteOrder: ByteOrder = ByteOrder.BIG_ENDIAN,
                       headerSize: Int = 4,
                       lengthIncludesHeader: Boolean = true) extends SymmetricPipelineStage[PipelineContext, ByteString, ByteString] {

  override def apply(ctx: PipelineContext) = new SymmetricPipePair[ByteString, ByteString] {
    var buffer = None: Option[ByteString]
    implicit val byteOrder = LengthFieldFrame.this.byteOrder

    /**
     * Extract as many complete frames as possible from the given ByteString
     * and return the remainder together with the extracted frames in reverse
     * order.
     */
    @tailrec
    def extractFrames(bs: ByteString, acc: List[ByteString]): (Option[ByteString], Seq[ByteString]) = {
      if (bs.isEmpty) {
        (None, acc)
      } else if (bs.length < headerSize) {
        (Some(bs.compact), acc)
      } else {
        val length = bs.iterator.getLongPart(headerSize).toInt
        if (length < 0 || length > maxSize)
          throw new IllegalArgumentException(
            s"received too large frame of size $length (max = $maxSize)")
        val total = if (lengthIncludesHeader) length else length + headerSize
        if (bs.length >= total) {
          extractFrames(bs drop total, bs.slice(headerSize, total) :: acc)
        } else {
          (Some(bs.compact), acc)
        }
      }
    }

    /*
     * This is how commands (writes) are transformed: calculate length
     * including header, write that to a ByteStringBuilder and append the
     * payload data. The result is a single command (i.e. `Right(...)`).
     */
    override def commandPipeline = {
      bs: ByteString ⇒
        val length =
          if (lengthIncludesHeader) bs.length + headerSize else bs.length
        if (length > maxSize) Seq()
        else {
          val bb = ByteString.newBuilder
          bb.putLongPart(length, headerSize)
          bb ++= bs
          ctx.singleCommand(bb.result)
        }
    }

    /*
     * This is how events (reads) are transformed: append the received
     * ByteString to the buffer (if any) and extract the frames from the
     * result. In the end store the new buffer contents and return the
     * list of events (i.e. `Left(...)`).
     */
    override def eventPipeline = {
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

//class Terminal(settings: SerialSettings) extends Actor with ActorLogging {
//
//  import Terminal._
//  import context._
//
//  override def preStart() = {
//    log.info(s"Requesting manager to open port: ${settings.port}, baud: ${settings.baud}")
//    IO(Serial) ! Serial.Open(settings)
//  }
//
//  def hex2bytes(hex: String): Array[Byte] = {
//    hex.replaceAll("[^0-9A-Fa-f]", "").sliding(2, 2).toArray.map(Integer.parseInt(_, 16).toByte)
//  }
//
//
//  def receive = {
//    case CommandFailed(cmd: Open, reason: AccessDeniedException) => println("you're not allowed to open that port!")
//    case CommandFailed(cmd: Open, reason) => println("could not open port for some other reason: " + reason.getMessage)
//    case Opened(settings, op) => {
//      val operator = sender
//      operator ! Register(self)
//      operator ! Write(ByteString.apply(hex2bytes("7E 00 04 08 01 4E 44 64")))
//      log.info("opened")
//    }
//    case Received(data) => println("got data: " + data.toString)
//  }
//
//  override def postStop() = {
//    system.shutdown()
//  }
//}
//
//object Terminal {
//
//  case class Wrote(data: ByteString) extends Event
//
//  def apply(settings: SerialSettings) = Props(classOf[Terminal], settings)
//
//  private def formatData(data: ByteString) = data.mkString("[", ",", "]") + " " + (new String(data.toArray, "UTF-8"))
//}