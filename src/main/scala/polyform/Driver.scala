package polyform

import akka.actor.{Actor, ActorLogging, ActorRef, Props, Stash}
import com.ctc.polyform.Protocol.CellZ
import polyform.Device.AsIsMoveEvent
import polyform.Driver.{MoveOp, PerformOp, _}

object Driver {
  def props(x: Int, y: Int, pub: ActorRef) = Props(new Driver(x, y, pub))

  val stepSize = 1

  private case object PerformOp
  private type MoveOp = Int => Int
  private def up(l: Int, r: Int): Int = l + r
  private def down(l: Int, r: Int): Int = l - r
}

class Driver(x: Int, y: Int, pub: ActorRef) extends Actor with Stash with ActorLogging {
  val deviceId = context.parent.path.name
  println(s"created $deviceId :: $x, $y")

  def waiting(z: Int): Receive = {
    unstashAll()

    {
      case CellZ(`x`, `y`, tobe) if tobe != z =>
        val dir = if (z < tobe) up(_, stepSize) else down(_, stepSize)
        context.become(move(z, tobe, dir))
    }
  }

  def move(asis: Int, tobe: Int, op: MoveOp): Receive = {
    self ! PerformOp

    {
      case CellZ(`x`, `y`, z) if tobe != z =>
        stash()
        context.become(waiting(asis))

      case PerformOp if asis == tobe =>
        context.become(waiting(asis))

      case PerformOp =>
        val to = op(asis)
        pub ! AsIsMoveEvent(deviceId, CellZ(x, y, to))
        context.become(move(to, tobe, op))
    }
  }

  def receive = waiting(0)
}
