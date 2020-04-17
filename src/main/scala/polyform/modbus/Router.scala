package polyform.modbus

import akka.actor.{Actor, ActorLogging, Props}
import polyform.Controller.{AsIsMoveEvent, MovementRequest}
import polyform.api.Device

object Router {
  def props(): Props = Props(new Router)
}

class Router extends Actor with ActorLogging {
  def ready(devices: Map[String, Seq[Device]]): Receive = {
    case d: Device =>
      context.become(
        ready(
          devices + (d.id -> (devices.getOrElse(d.id, Seq.empty) :+ d))
        )
      )

    case e @ AsIsMoveEvent(dev, _) =>
      devices.get(dev).foreach(_.foreach(_.mem ! e))

    case e @ MovementRequest(dev, _) =>
      devices.get(dev).foreach(_.foreach(_.ref ! e))
  }

  def receive: Receive = ready(Map.empty)
}
