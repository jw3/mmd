package polyform.modbus

import akka.actor.{ActorRef, ActorSystem}
import com.ctc.polyform.Protocol.ModuleConfig
import com.digitalpetri.modbus.slave.ModbusTcpSlaveConfig
import polyform.{api, Controller}

object boot extends App {
  private val system = ActorSystem("modbus-mockdev")
  private val config: ModbusTcpSlaveConfig = new ModbusTcpSlaveConfig.Builder().build()
  private val hostname = "localhost"

  // create vr memory
  val mem: Map[String, ActorRef] = api.DeviceIDs
    .map(xy => s"${xy._1}_${xy._2}" -> xy)
    .map(xy => xy._1 -> system.actorOf(MemoryVR.props(), s"${xy._1}-memory"))
    .toMap

  // create devices
  val devices = api.DeviceIDs
    .map(xy => s"${xy._1}_${xy._2}" -> xy)
    .map(xy =>
      xy._1 -> system.actorOf(Controller.props(mem(xy._1), ModuleConfig(xy._2._1, xy._2._2, 8, 8, None)), xy._1)
    )
    .toMap

  // create modbus slaves
  api.DeviceIDs
    .map(xy => s"${xy._1}_${xy._2}")
    .map(xy => AkkaSlave(mem(xy), config))
    .zipWithIndex
    .foreach {
      case (s, i) =>
        s.bind(hostname, 50200 + i)
    }
}
