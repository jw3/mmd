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
      case CellZ(`x`, `y`, tobe) if z == tobe =>
        log.debug("{},{} already at {}", x, y, z)

      case CellZ(`x`, `y`, tobe) =>
        val dir = if (z < tobe) up(_, stepSize) else down(_, stepSize)
        //println(s"$x,$y moving")
        context.become(move(z, tobe, dir))
    }
  }

  def move(asis: Int, tobe: Int, op: MoveOp): Receive = {
    import scala.concurrent.duration.DurationInt
    timers.startPeriodicTimer(PerformOp, PerformOp, 1.second / 60)

    var to = asis

    {
      // nop; already there
      case CellZ(`x`, `y`, z) if tobe == z =>
        //println(s"$x,$y nop")
        timers.cancel(PerformOp)
        context.become(waiting(asis))

      // change in destination
      case CellZ(`x`, `y`, z) if tobe != z =>
        //println(s"$x,$y dir change")
        stash()
        timers.cancel(PerformOp)
        context.become(waiting(to))

      // movement complete
      case PerformOp if to == tobe =>
        //println(s"$x,$y timer 1")
        timers.cancel(PerformOp)
        // todo;; fire event
        context.become(waiting(to))

      // movement, throttled by the timer
      case PerformOp =>
        //println(s"$x,$y timer 2 $to =?= $tobe")
        to = op(to)
        pub ! AsIsMoveEvent(deviceId, CellZ(x, y, to))
    }
  }

  def receive: Receive = waiting(0)

  override def unhandled(message: Any): Unit =
    println(s"========= ${context.self.path.name} unhandled $message ===========")
}
