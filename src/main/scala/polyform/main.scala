package polyform

import akka.Done
import akka.actor.ActorSystem
import akka.stream.alpakka.mqtt.scaladsl.{MqttSink, MqttSource}
import akka.stream.alpakka.mqtt.{MqttConnectionSettings, MqttMessage, MqttQoS, MqttSubscriptions}
import akka.stream.scaladsl.{Keep, Sink, Source}
import akka.stream.{ActorMaterializer, OverflowStrategy}
import akka.util.ByteString
import com.ctc.polyform.Protocol
import com.ctc.polyform.Protocol.CellZ
import com.ctc.polyform.Protocol.Topics._
import com.typesafe.scalalogging.LazyLogging
import net.ceedubs.ficus.Ficus._
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import polyform.Controller.AsIsMoveEvent
import polyform.Px.{connectionSettings, tobeChannel, topicPrefix}
import requests.Response

import scala.concurrent.Future

object Px extends LazyLogging {
  implicit val system: ActorSystem = ActorSystem()
  implicit val materializer: ActorMaterializer = ActorMaterializer()
  val config = system.settings.config

  val asisChannel = config.as[String]("channel.asis")
  val tobeChannel = config.as[String]("channel.tobe")
  val topicPrefix = s"xr"

  val tobeFunc = "move"
  val asisFunc = PositionUpdate

  private val mmps: Int = config.getAs[Int]("sim.speed").getOrElse(75)
  val mmpsDelay: Int = 1000 / mmps
  val stepSize: Int = 1

  private val cloudHost = config.as[String]("cloud.host")
  private val cloudPort = config.getAs[Int]("cloud.port").getOrElse(9000)
  val api = s"http://$cloudHost:$cloudPort/v1/"

  private val Unused = "__unused__"
  private val brokerHost = config.as[String]("mqtt.host")
  private val brokerPort = config.getAs[Int]("mqtt.port").getOrElse(1883)
  val mqttUri = s"tcp://$brokerHost:$brokerPort"
  val connectionSettings = MqttConnectionSettings(mqttUri, Unused, new MemoryPersistence)

  def publish(topic: String, data: String): Response =
    requests.post(api + "devices/events", data = Map("name" → topic, "data" → data))

  def up(l: Int, r: Int): Int = l + r
  def down(l: Int, r: Int): Int = l - r
}

object main extends App with LazyLogging {
  import Px.materializer

  logger.info("connecting to {}", Px.mqttUri)
  def S(R: Boolean, M: Boolean) = s"""{"ready":$R,"moving":$M}"""
  def P(cz: CellZ) = s"""[{"x":${cz.x},"y":${cz.y},"z":${cz.z.toInt}}]"""

  val mqttSink: Sink[MqttMessage, Future[Done]] =
    MqttSink(connectionSettings.withClientId(s"mock_publisher"), MqttQoS.atLeastOnce)
  val publisher = Source
    .actorRef[AsIsMoveEvent](10000, OverflowStrategy.fail)
    .map {
      case AsIsMoveEvent(dev, cz) =>
        MqttMessage(s"/$topicPrefix/${Px.asisChannel}/$dev/move", ByteString(P(cz)))
    }
    .toMat(mqttSink)(Keep.left)
    .run()

  // create device actors
  val devices =
    Seq((0, 0), (0, 1), (0, 2), (0, 3))
      .map(xy => s"${xy._1}_${xy._2}" -> xy)
      .map(xy =>
        xy._1 -> Px.system
          .actorOf(Controller.props(publisher, Protocol.ModuleConfig(xy._2._1, xy._2._2, 8, 8, None)), xy._1)
      )
      .toMap

  // subscribe to mqtt
  devices.foreach {
    case (name, ref) =>
      MqttSource
        .atMostOnce(
          Px.connectionSettings.withClientId(name),
          MqttSubscriptions(s"$topicPrefix/$tobeChannel/$name/move", MqttQoS.atLeastOnce),
          bufferSize = 10000
        )
        .alsoTo(Sink.foreach(println))
        .toMat(Sink.actorRef(ref, Done))(Keep.both)
        .run()
  }
}

//  Px.publish(s"$topicPrefix/$deviceId/$StateUpdate", S(ready, aligning))
//
// Px.publish(s"$topicPrefix/$deviceId/pos", P(cz))
//  Source
//    .fromIterator(() => all.iterator)
//    .map { cz =>
//      MqttMessage(s"/$topicPrefix/$asisChannel/$deviceId/move", ByteString(P(cz)))
//    }
//    .runWith(Px.mqttSink)
