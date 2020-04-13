package polyform

import akka.Done
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.alpakka.mqtt.scaladsl.MqttSource
import akka.stream.alpakka.mqtt.{MqttConnectionSettings, MqttQoS, MqttSubscriptions}
import akka.stream.scaladsl.{Keep, Sink}
import com.ctc.polyform.Protocol.Topics._
import com.ctc.polyform.Protocol.{CellZ, Module, ModuleConfig}
import com.typesafe.scalalogging.LazyLogging
import net.ceedubs.ficus.Ficus._
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import polyform.Px.{deviceId, prefix}
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
  val channel = "default"
  val prefix = s"xr/$channel"

  val tobeFunc = "move"
  val asisFunc = PositionUpdate

  val api = "http://localhost:9000/v1/"
  private val Unused = "__unused__"
  private val brokerHost = config.as[String]("mqtt.host")
  private val brokerPort = config.as[Int]("mqtt.port")
  val mqttUri = s"tcp://$brokerHost:$brokerPort"
  private val connectionSettings = MqttConnectionSettings(mqttUri, Unused, new MemoryPersistence)

  def publish(topic: String, data: String): Response =
    requests.post(api + "devices/events", data = Map("name" → topic, "data" → data))

  def function(name: String, fn: String ⇒ Unit): Future[Done] = {
    val (subscribed, result) = MqttSource
      .atMostOnce(
        connectionSettings.withClientId(deviceId + name),
        MqttSubscriptions(s"$prefix/$deviceId/$name", MqttQoS.atLeastOnce),
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
  def P(cz: CellZ) = s"""{"x":${cz.x},"y":${cz.y},"z":${cz.z}}"""

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
    println(s"move: $v")
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

      println(moves.mkString(","))

      moves match {
        case Nil ⇒
          aligning = false
          Px.publish(s"$prefix/$deviceId/$StateUpdate", S(ready, aligning))
          println("alignment completed")
        case all ⇒
          all.foreach(cz ⇒ Px.publish(s"$prefix/$deviceId/${Px.asisFunc}", P(cz)))
      }
    }
    Thread.sleep(1000)
  }
}
