package polyform

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import com.ctc.polyform.Protocol.{CellZ, Module, ModuleConfig}
import polyform.Controller.MovementRequest

/**
  * controller; manages up to 64 drivers
  */
object Controller {
  def props(pub: ActorRef, mc: ModuleConfig) = Props(new Controller(pub, mc))

  sealed trait DeviceEvent
  case class AlignmentComplete(deviceId: String) extends DeviceEvent
  case class AsIsMoveEvent(deviceId: String, cz: CellZ) extends DeviceEvent
  case class LegacyPositionEvent(deviceId: String, cells: Seq[CellZ]) extends DeviceEvent

  private case class AlignmentStepCompleted(became: Module)

  case class MovementRequest(deviceId: String, cells: Seq[CellZ])
}

class Controller(pub: ActorRef, mc: ModuleConfig) extends Actor with ActorLogging {
  private val deviceId = context.self.path.name

  def driver(x: Int, y: Int): Option[ActorRef] = context.child(s"${x}_$y")

  def receive: Receive = {
    for (i <- 0 until mc.w * mc.h) {
      val x = i % mc.h
      val y = i / mc.w
      context.actorOf(Driver.props(x, y, pub), s"${x}_$y")
    }
    println(s"device $deviceId ready!")

    {
      case MovementRequest(_, cells) =>
        cells.flatMap(cz => driver(cz.x, cz.y).map(cz -> _)).foreach { x =>
          x._2 ! x._1
        }
    }
  }

  override def unhandled(message: Any): Unit = println(message)
}
