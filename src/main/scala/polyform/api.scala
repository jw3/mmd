package polyform

import akka.actor.ActorRef

object api {
  val DeviceIDs = Seq((0, 0), (1, 0), (2, 0), (3, 0))
  case class Device(id: String, ref: ActorRef, mem: ActorRef)
}
