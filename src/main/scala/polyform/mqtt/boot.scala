package polyform.mqtt

import akka.Done
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Keep, Sink}
import com.ctc.polyform.Protocol.{CellZ, ModuleConfig}
import com.typesafe.scalalogging.LazyLogging
import net.ceedubs.ficus.Ficus._
import polyform.{api, Controller}
import requests.Response

object boot extends App with LazyLogging {
  implicit val system: ActorSystem = ActorSystem("mqtt-mockdev")
  implicit val materializer: ActorMaterializer = ActorMaterializer()
  val config = system.settings.config

  val tobeChannel: String = config.as[String]("channel.tobe")
  val channelPrefix: String = config.as[String]("channel.prefix")

  private val mmps: Int = config.getAs[Int]("sim.speed").getOrElse(75)
  val mmpsDelay: Int = 1000 / mmps
  val stepSize: Int = 1

  private val cloudHost = config.as[String]("cloud.host")
  private val cloudPort = config.getAs[Int]("cloud.port").getOrElse(9000)
  val apiUri = s"http://$cloudHost:$cloudPort/v1/"

  val connectionSettings = PubSub.settings(config)
  logger.info("connecting to {}", connectionSettings.broker)
  def S(R: Boolean, M: Boolean) = s"""{"ready":$R,"moving":$M}"""
  def P(cz: CellZ) = s"""[{"x":${cz.x},"y":${cz.y},"z":${cz.z.toInt}}]"""

  val publisher = PubSub.pub(config)

  // create device actors
  val devices = api.DeviceIDs
    .map(xy => s"${xy._1}_${xy._2}" -> xy)
    .map(xy => xy._1 -> system.actorOf(Controller.props(publisher, ModuleConfig(xy._2._1, xy._2._2, 8, 8, None)), xy._1)
    )
    .toMap

  // subscribe to mqtt
  devices.foreach {
    case (deviceId, ref) =>
      PubSub
        .sub(deviceId, config)
        .toMat(Sink.actorRef(ref, Done))(Keep.both)
        .run()
  }

  def publish(api: String, topic: String, data: String): Response =
    requests.post(api + "devices/events", data = Map("name" → topic, "data" → data))

  def up(l: Int, r: Int): Int = l + r
  def down(l: Int, r: Int): Int = l - r
}
