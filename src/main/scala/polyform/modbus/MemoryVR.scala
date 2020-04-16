package polyform.modbus

import akka.actor.{Actor, ActorLogging, Props}
import polyform.Controller.AsIsMoveEvent
import polyform.modbus.MemoryVR.{MemMap, ModifyVR, RequestVR, VR}

object MemoryVR {
  private type XY = (Int, Int)
  def props(): Props = Props(new MemoryVR)

  def xy(x: Int, y: Int): Int = y << 6 | x
  def x(vr: Int): Int = vr & 0x003F
  def y(vr: Int): Int = vr >> 6 & 0x003F
  def x_y(vr: Int): String = s"${x(vr)}_${y(vr)}"

  case class ModifyVR(address: Int, value: Int)
  case class RequestVR(address: Int)
  case class VR(value: Int)

  type MemMap = Map[XY, Int]
  def table(): MemMap = {
    val vrs = for {
      xx <- 0 until 63
      yy <- 0 until 63
    } yield (xx, yy) -> 0
    vrs.toMap
  }
}

class MemoryVR extends Actor with ActorLogging {
  var table: MemMap = MemoryVR.table()

  def receive: Receive = {
    case ModifyVR(address, value) =>
      table += (MemoryVR.x(address) -> MemoryVR.y(address)) -> value
      sender ! VR(value)

    case RequestVR(address) ⇒
      table.get(MemoryVR.x(address) -> MemoryVR.y(address)).foreach { value =>
        sender ! VR(value)
      }
    case AsIsMoveEvent(_, cz) =>
      table += (cz.x, cz.y) -> cz.z
  }
}
