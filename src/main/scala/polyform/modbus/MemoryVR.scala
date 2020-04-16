package polyform.modbus

import akka.actor.{Actor, ActorLogging, Props}
import com.ctc.polyform.Protocol.CellZ
import polyform.Controller.AsIsMoveEvent
import polyform.modbus.MemoryVR._

object MemoryVR {
  private type XY = (Int, Int)
  private type MemMap = Map[XY, Int]

  def props(): Props = Props(new MemoryVR)

  def xy(x: Int, y: Int): Int = y << 6 | x
  def x(vr: Int): Int = vr & 0x003F
  def y(vr: Int): Int = vr >> 6 & 0x003F
  def x_y(vr: Int): XY = x(vr) -> y(vr)
  def x_y(cz: CellZ): XY = cz.x -> cz.y

  case class ModifyVR(address: Int, value: Int)
  case class RequestVR(address: Int)
  case class VR(value: Int)
}

class MemoryVR extends Actor with ActorLogging {
  var table: MemMap = Map.empty

  def receive: Receive = {
    case ModifyVR(address, value) =>
      table += x_y(address) -> value
      sender ! VR(value)

    case RequestVR(address) ⇒
      val value = table.getOrElse(x_y(address), 0)
      sender ! VR(value)

    case AsIsMoveEvent(_, cz) =>
      table += x_y(cz) -> cz.z
  }
}
