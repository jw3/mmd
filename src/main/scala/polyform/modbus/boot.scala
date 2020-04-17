package polyform.modbus

import akka.actor.{ActorRef, ActorSystem}
import com.ctc.polyform.Protocol.ModuleConfig
import com.digitalpetri.modbus.slave.ModbusTcpSlaveConfig
import polyform.api.Device
import polyform.{api, Controller}

object boot extends App {
  private val system = ActorSystem("modbus-mockdev")
  private val config: ModbusTcpSlaveConfig = new ModbusTcpSlaveConfig.Builder().build()
  private val hostname = "localhost"
  private val baseport = 50200

  // stringified device ids
  val deviceNames = api.DeviceIDs.map(xy => s"${xy._1}_${xy._2}" -> xy)

  // a single router for all devices
  val router = system.actorOf(Router.props(), "router")

  // VR memory
  val mem: Map[String, ActorRef] = deviceNames.map { xy =>
    xy._1 -> system.actorOf(MemoryVR.props(xy._1, router), s"${xy._1}-memory")
  }.toMap

  // movement controller devices
  val devices: Map[String, Device] = deviceNames
    .map(xy => xy._1 -> system.actorOf(Controller.props(router, ModuleConfig(xy._2._1, xy._2._2, 8, 8, None)), xy._1))
    .toMap
    .zip(mem)
    .map(e => e._1._1 -> Device(e._1._1, e._1._2, e._2._2))

  // install devices in router
  devices.values.foreach(router ! _)

  // create modbus slave per device
  api.DeviceIDs
    .map(xy => s"${xy._1}_${xy._2}")
    .map(xy => AkkaSlave(mem(xy), config))
    .zipWithIndex
    .foreach {
      case (s, i) =>
        s.bind(hostname, baseport + i)
    }
}
