package polyform.modbus

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import com.ctc.polyform.Protocol.CellZ
import polyform.Controller.{AsIsMoveEvent, MovementRequest}
import polyform.api.telemetry
import polyform.modbus.MemoryVR._

object MemoryVR {
  private type Addr = Int
  private type XY = (Int, Int)
  private type MemMap = Map[Addr, Int]

  def props(id: String, pub: ActorRef): Props = Props(new MemoryVR(id, pub))

  def xy(x: Int, y: Int): Int = y << 6 | x
  def x(vr: Int): Int = vr & 0x003F
  def y(vr: Int): Int = vr >> 6 & 0x003F
  def x_y(vr: Int): XY = x(vr) -> y(vr)
  def x_y(cz: CellZ): XY = cz.x -> cz.y

  case class ModifyVR(address: Int, value: Int)
  case class RequestVR(address: Int)
  case class VR(value: Int)
}

class MemoryVR(deviceId: String, pub: ActorRef) extends Actor with ActorLogging {
  var table: MemMap = Map.empty

  def receive: Receive = {
    case ModifyVR(addr, value) =>
      table += addr -> value
      sender ! VR(value)

      val xy = x_y(addr)
      pub ! MovementRequest(deviceId, Seq(CellZ(xy._1, xy._2, value)))

    case RequestVR(addr) ⇒
      val value = table.getOrElse(addr, 0)
      sender ! VR(value)

    case AsIsMoveEvent(_, cz) =>
      val addr = telemetry.Mpos.vr(cz.x, cz.y)
      table += addr -> cz.z
  }
}
