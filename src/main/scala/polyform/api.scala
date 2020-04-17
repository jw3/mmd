package polyform

import akka.actor.ActorRef

object api {
  val DeviceIDs = Seq((0, 0), (1, 0), (2, 0), (3, 0))
  case class Device(id: String, ref: ActorRef, mem: ActorRef)

  object telemetry {
    private val TotalParams = 24
    private val TelemetryOffset = 15000
    private val HardCoded_x_max = 8
    private val HardCoded_y_max = 8

    private def axis(x: Int, y: Int): Int =
      if (HardCoded_x_max < HardCoded_y_max) (HardCoded_x_max + 1) * x + y else (HardCoded_y_max + 1) * y + x

    sealed trait Param {
      def offset: Int

      def vr(x: Int, y: Int): Int =
        axis(x, y) * TotalParams + offset + TelemetryOffset
    }

    object Units extends Param { val offset = 0 }
    object Speed extends Param { val offset = 1 }
    object Accel extends Param { val offset = 2 }
    object Decel extends Param { val offset = 3 }
    object Dpos extends Param { val offset = 4 }
    object Mpos extends Param { val offset = 5 }
  }
}
