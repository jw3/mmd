package polyform

import akka.actor.{Actor, ActorLogging, ActorRef, Props, Stash, Timers}
import com.ctc.polyform.Protocol.CellZ
import polyform.Controller.AsIsMoveEvent
import polyform.Driver.{MoveOp, PerformOp, _}

/**
  * driver; controls the movement of a single x,y column
  */
object Driver {
  def props(x: Int, y: Int, pub: ActorRef): Props = Props(new Driver(x, y, pub))

  val stepSize = 1

  private case object PerformOp
  private type MoveOp = Int => Int
  private def up(l: Int, r: Int): Int = l + r
  private def down(l: Int, r: Int): Int = l - r
}

class Driver(x: Int, y: Int, pub: ActorRef) extends Actor with Stash with Timers with ActorLogging {
  private val deviceId = context.parent.path.name
  log.info(s"$deviceId :: $x, $y :: ready!")

  def waiting(z: Int): Receive = {
    unstashAll()

    {
      case CellZ(`x`, `y`, tobe) if tobe != z =>
        val dir = if (z < tobe) up(_, stepSize) else down(_, stepSize)
        context.become(move(z, tobe, dir))
    }
  }

  def move(asis: Int, tobe: Int, op: MoveOp): Receive = {
    import scala.concurrent.duration.DurationInt
    timers.startPeriodicTimer(PerformOp, PerformOp, 1.second / 60)

    var to = asis

    {
      // represents a change in destination
      case CellZ(`x`, `y`, z) if tobe != z =>
        stash()
        timers.cancel(PerformOp)
        context.become(waiting(to))

      case PerformOp if asis == tobe =>
        timers.cancel(PerformOp)
        // todo;; fire event
        context.become(waiting(asis))

      // the move operation, needs throttled
      case PerformOp =>
        to = op(to)
        pub ! AsIsMoveEvent(deviceId, CellZ(x, y, to))
    }
  }

  def receive: Receive = waiting(0)
}
