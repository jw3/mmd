package polyform.modbus

import akka.actor.{Actor, ActorLogging, Props}
import polyform.Controller.AsIsMoveEvent
import polyform.modbus.MemoryVR.{RequestVR, VR, XY}

object MemoryVR {
  private type XY = (Int, Int)
  def props(): Props = Props(new MemoryVR)

  def xy(x: Int, y: Int): Int = y << 6 | x
  def x(vr: Int): Int = vr & 0x003F
  def y(vr: Int): Int = vr >> 6 & 0x003F
  def x_y(vr: Int): String = s"${x(vr)}_${y(vr)}"

  case class RequestVR(address: Int)
  case class VR(data: Int)
}

class MemoryVR extends Actor with ActorLogging {
  var store: Map[XY, Int] = {
    val vrs = for {
      xx <- 0 until 63
      yy <- 0 until 63
    } yield (xx, yy) -> 0
    vrs.toMap
  }

  def receive: Receive = {
    case RequestVR(address) ⇒
      store.get(MemoryVR.x(address) -> MemoryVR.y(address)).foreach { data =>
        sender ! VR(data)
      }
    case AsIsMoveEvent(_, cz) =>
      store += (cz.x, cz.y) -> cz.z
  }
}
