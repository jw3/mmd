package polyform

object main extends App {
  object Connectors {
    val Mqtt = "mqtt"
    val Modbus = "modbus"
  }

  args match {
    case Array(Connectors.Mqtt)   => mqtt.boot.main(args.drop(1))
    case Array(Connectors.Modbus) => modbus.boot.main(args.drop(1))
    case Array() =>
      println("""
          |usage: mockdevice <connector>
          |                  - mqtt
          |                  - modbus
          |""".stripMargin)
  }
}
