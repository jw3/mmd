package polyform

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.stream.alpakka.mqtt.MqttMessage
import com.ctc.polyform.Protocol.{CellZ, Module, ModuleConfig}
import polyform.Device.{AlignmentComplete, AlignmentStepCompleted, AsIsMoveEvent}
import spray.json._

object Device {
  def props(id: String, pub: ActorRef, mc: ModuleConfig) = Props(new Device(id, pub, mc))

  sealed trait DeviceEvent
  case class AlignmentComplete(deviceId: String) extends DeviceEvent
  case class AsIsMoveEvent(deviceId: String, cz: CellZ) extends DeviceEvent
  case class LegacyPositionEvent(deviceId: String, cells: Seq[CellZ]) extends DeviceEvent

  private case class AlignmentStepCompleted(became: Module)
}

class Device(id: String, pub: ActorRef, mc: ModuleConfig) extends Actor with ActorLogging {
  def ready(asis: Module): Receive = {
    println(s"device $id ready!")

    {
      case e: MqttMessage =>
        println("recv")
        val tobe = Module(mc).set(e.payload.utf8String.parseJson.convertTo[CellZ])
        context.become(aligning(Module(mc), tobe))
    }
  }

  def aligning(asis: Module, tobe: Module): Receive = {
    val moves: Seq[CellZ] = for {
      row ← 0 to mc.h
      col ← 0 to mc.w
      nowz ← asis.get(col, row)
      futz ← tobe.get(col, row)
      if nowz.z != futz.z
      op = if (futz.z > nowz.z) Px.up _ else Px.down _
    } yield {
      val z = CellZ(col, row, op(nowz.z.toInt, Px.stepSize))
      pub ! AsIsMoveEvent(id, z)
      z
    }

    val became = moves match {
      case Nil ⇒
        println("alignment completed")
        pub ! AlignmentComplete(id)
        context.become(ready(asis))
        None
      case all ⇒
        println(moves.map(cz => s"{x:${cz.x} y:${cz.y} z:${cz.z}}").mkString(", "))

        //pub ! LegacyPositionEvent(id, all)

        val became = all.foldLeft(asis)((m, z) => m.set(z))
        self ! AlignmentStepCompleted(became)
        Some(became)
    }

    {
      case e: MqttMessage =>
        val addtlTobe = Module(mc).set(e.payload.utf8String.parseJson.convertTo[CellZ])
        became.foreach(m => context.become(aligning(m, tobe.overlay(addtlTobe))))

      case AlignmentStepCompleted(m) =>
        context.become(aligning(m, tobe))
    }
  }

  def receive: Receive = ready(Module(mc))

  override def unhandled(message: Any): Unit = println(message)
}
