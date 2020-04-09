package polyform

import akka.Done
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.alpakka.mqtt.scaladsl.MqttSource
import akka.stream.alpakka.mqtt.{MqttConnectionSettings, MqttQoS, MqttSubscriptions}
import akka.stream.scaladsl.{Keep, Sink}
import com.ctc.polyform.Protocol.Topics._
import com.ctc.polyform.Protocol.{CellZ, Module, ModuleConfig, ParticleDevice}
import com.typesafe.scalalogging.LazyLogging
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import requests.Response
import spray.json._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object Px extends LazyLogging {
  implicit val system: ActorSystem = ActorSystem()
  implicit val materializer: ActorMaterializer = ActorMaterializer()

  val deviceId = "d001"
  val api = "http://localhost:9000/v1/"
  private val Unused = "__unused__"
  val brokerHost = "localhost" //config.as[String]("mqtt.host")
  val brokerPort = 1883 //config.as[Int]("mqtt.port")
  val connectionSettings = MqttConnectionSettings(s"tcp://$brokerHost:$brokerPort", Unused, new MemoryPersistence)

  def publish(topic: String, data: String): Response =
    requests.post(api + "devices/events", data = Map("name" → topic, "data" → data))

  def function(name: String, fn: String ⇒ Unit): Future[Done] = {
    val (subscribed, result) = MqttSource
      .atMostOnce(
        connectionSettings.withClientId(deviceId + name),
        MqttSubscriptions(s"/F/$deviceId/$name", MqttQoS.atLeastOnce),
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

object main extends App {
  def R(dev: ParticleDevice) = dev.toJson.compactPrint
  def S(R: Boolean, M: Boolean) = s"""{"ready":$R,"moving":$M}"""
  def P(cz: CellZ) = s"""{"x":${cz.x},"y":${cz.y},"z":${cz.z}}"""

  var ready = false
  var aligning = false
  var config: Option[ModuleConfig] = None
  var moduleAsIs: Option[Module] = None
  var moduleToBe: Option[Module] = None

  def configure(v: String) = {
    require(config.isEmpty, "configuration was already specified")

    val cfg = v.parseJson.convertTo[ModuleConfig]
    config = Some(cfg)
    moduleAsIs = Some(Module(cfg))

    ready = config.isDefined
    Px.publish(s"/xr/default/0_0/$StateUpdate", S(ready, aligning))
  }

  def addNode(v: String) = {
    require(config.nonEmpty, "configuration was not specified")

    if (moduleToBe.isEmpty) moduleToBe = config.map(Module(_))
    moduleToBe = moduleToBe.map(_.set(v.parseJson.convertTo[CellZ]))
    println(s"addNode: $v")
  }

  def align(s: String) = {
    require(config.isDefined, "configuration is not defined")

    aligning = true
    Px.publish(s"xr/0-0/$StateUpdate", S(ready, aligning))
  }

  def cancel(s: String) = {
    moduleToBe = None
    aligning = false
    Px.publish(s"xr/0-0/$StateUpdate", S(ready, aligning))
  }

  val setCfgFn = Px.function("setConfig", configure)
  val addNodesFn = Px.function("addNodes", addNode)
  val alignFn = Px.function("align", align)
  val cancelFn = Px.function("cancel", cancel)

  Px.publish(s"xr/$Register", R(ParticleDevice(Px.deviceId)))

  var steps: Int = 0
  while (true) {

    if (aligning) {
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
        if nowz.z < futz.z
      } {
        val cz = CellZ(col, row, nowz.z + 1)
        moduleAsIs = Some(asis.set(cz))
        moves :+= cz
      }

      println(moves.mkString(","))

      moves match {
        case Nil ⇒
          aligning = false
          Px.publish(s"xr/0-0/$StateUpdate", S(ready, aligning))
          println("alignment completed")
        case all ⇒
          all.foreach(cz ⇒ Px.publish(s"xr/0-0/$PositionUpdate", P(cz)))
      }
    }
    Thread.sleep(1000)
  }
}

