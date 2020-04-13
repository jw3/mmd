package polyform

import akka.Done
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.alpakka.mqtt.scaladsl.{MqttSink, MqttSource}
import akka.stream.alpakka.mqtt.{MqttConnectionSettings, MqttMessage, MqttQoS, MqttSubscriptions}
import akka.stream.scaladsl.{Keep, Sink, Source}
import akka.util.ByteString
import com.ctc.polyform.Protocol.Topics._
import com.ctc.polyform.Protocol.{CellZ, Module, ModuleConfig}
import com.typesafe.scalalogging.LazyLogging
import net.ceedubs.ficus.Ficus._
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import polyform.Px.{asisChannel, deviceId, topicPrefix}
import requests.Response
import spray.json._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.Try

object Px extends LazyLogging {
  implicit val system: ActorSystem = ActorSystem()
  implicit val materializer: ActorMaterializer = ActorMaterializer()
  val config = system.settings.config

  val deviceId = "0_0"
  val asisChannel = config.as[String]("channel.asis")
  val tobeChannel = config.as[String]("channel.tobe")
  val topicPrefix = s"xr"

  val tobeFunc = "move"
  val asisFunc = PositionUpdate

  private val cloudHost = config.as[String]("cloud.host")
  private val cloudPort = config.getAs[Int]("cloud.port").getOrElse(9000)
  val api = s"http://$cloudHost:$cloudPort/v1/"

  private val Unused = "__unused__"
  private val brokerHost = config.as[String]("mqtt.host")
  private val brokerPort = config.getAs[Int]("mqtt.port").getOrElse(1883)
  val mqttUri = s"tcp://$brokerHost:$brokerPort"
  private val connectionSettings = MqttConnectionSettings(mqttUri, Unused, new MemoryPersistence)

  def publish(topic: String, data: String): Response =
    requests.post(api + "devices/events", data = Map("name" → topic, "data" → data))

  val mqttSink: Sink[MqttMessage, Future[Done]] =
    MqttSink(connectionSettings.withClientId(s"mock_$deviceId"), MqttQoS.atLeastOnce)

  def function(name: String, fn: String ⇒ Unit): Future[Done] = {
    val (subscribed, result) = MqttSource
      .atMostOnce(
        connectionSettings.withClientId(deviceId + name),
        MqttSubscriptions(s"$topicPrefix/$tobeChannel/$deviceId/$name", MqttQoS.atLeastOnce),
        bufferSize = 8
      )
      .toMat(Sink.foreach(e ⇒ fn(e.payload.utf8String)))(Keep.both)
      .run()


    subscribed.flatMap { x ⇒
      println(s"$name is subscribed")
      result.recover{
        case t ⇒
          logger.error("cloud function unsubscribed", t)
          Done
      }.map { done ⇒
        println(s"$name was closed")
        done
      }
    }
  }
}

object main extends App with LazyLogging {
  logger.info("connecting to {}", Px.mqttUri)
  def S(R: Boolean, M: Boolean) = s"""{"ready":$R,"moving":$M}"""
  def P(cz: CellZ) = s"""[{"x":${cz.x},"y":${cz.y},"z":${cz.z.toInt}}]"""

  val (x, y, w, h) = (0, 0, 8, 8)
  val hardargs = s"""{ "x": $x, "y": $y, "w": $w, "h": $h }"""

  var ready = true
  var config: Option[ModuleConfig] = Try(hardargs.parseJson.convertTo[ModuleConfig]).toOption
  var moduleAsIs: Option[Module] = config.map(Module(_))
  var moduleToBe: Option[Module] = config.map(Module(_))

  var aligning = false
  private def receiveMove(v: String): Unit = {
    require(config.nonEmpty, "configuration was not specified")
    moduleToBe = moduleToBe.map(_.set(v.parseJson.convertTo[CellZ]))
    aligning = true
  }
  val receiveMoveFn = Px.function(Px.tobeFunc, receiveMove)

  var steps: Int = 0
  while (true) {

    if (ready && aligning) {
      steps += 1

      var moves = List.empty[CellZ]
      for {
        cfg ← config
        row ← 0 to cfg.h
        col ← 0 to cfg.w
        asis ← moduleAsIs
        tobe ← moduleToBe
        nowz ← asis.get(col, row)
        futz ← tobe.get(col, row)
        if nowz.z < futz.z // todo;; need to move down too...
      } {
        val cz = CellZ(col, row, nowz.z + 1)
        moduleAsIs = Some(asis.set(cz))
        moves :+= cz
      }

      println(moves.map(cz => s"{x:${cz.x} y:${cz.y} z:${cz.z}}").mkString(", "))

      moves match {
        case Nil ⇒
          aligning = false
          Px.publish(s"$topicPrefix/$deviceId/$StateUpdate", S(ready, aligning))
          println("alignment completed")
        case all ⇒
          import Px.materializer

          // particle style telemetry
          all.foreach(cz => Px.publish(s"$topicPrefix/$deviceId/pos", P(cz)))

          // modbus style telemetry
          Source
            .fromIterator(() => all.iterator)
            .map { cz =>
              MqttMessage(s"/$topicPrefix/$asisChannel/$deviceId/move", ByteString(P(cz)))
            }
            .runWith(Px.mqttSink)

      }
    }
    Thread.sleep(100)
  }
}
