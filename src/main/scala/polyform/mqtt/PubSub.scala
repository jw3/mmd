package polyform.mqtt

import akka.Done
import akka.actor.ActorRef
import akka.stream.alpakka.mqtt.scaladsl.{MqttSink, MqttSource}
import akka.stream.alpakka.mqtt.{MqttConnectionSettings, MqttMessage, MqttQoS, MqttSubscriptions}
import akka.stream.scaladsl.{Keep, Sink, Source}
import akka.stream.{ActorMaterializer, OverflowStrategy}
import akka.util.ByteString
import com.ctc.polyform.Protocol.CellZ
import com.typesafe.config.Config
import net.ceedubs.ficus.Ficus._
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import polyform.Controller.{AsIsMoveEvent, MovementRequest}
import polyform.mqtt.boot.P

import scala.concurrent.Future

object PubSub {
  private val Unused = "__unused__"
  val tobeFunc = "move"
  val asisFunc = "move"

  def settings(config: Config): MqttConnectionSettings = {
    val brokerHost = config.as[String]("mqtt.host")
    val brokerPort = config.getAs[Int]("mqtt.port").getOrElse(1883)
    val mqttUri = s"tcp://$brokerHost:$brokerPort"
    MqttConnectionSettings(mqttUri, Unused, new MemoryPersistence)
  }

  def pub(config: Config)(implicit mat: ActorMaterializer): ActorRef = {
    val prefix: String = config.as[String]("channel.prefix")
    val asisChannel: String = config.as[String]("channel.asis")

    val cs = settings(config)

    val mqttSink: Sink[MqttMessage, Future[Done]] =
      MqttSink(cs.withClientId(s"mock_publisher"), MqttQoS.atLeastOnce)
    Source
      .actorRef[AsIsMoveEvent](1000, OverflowStrategy.dropHead)
      .map {
        case AsIsMoveEvent(dev, cz) =>
          MqttMessage(s"$prefix/${asisChannel}/$dev/move", ByteString(P(cz)))
      }
      .toMat(mqttSink)(Keep.left)
      .run()
  }

  def sub(deviceId: String, config: Config): Source[MovementRequest, Future[Done]] = {
    import spray.json._
    val prefix: String = config.as[String]("channel.prefix")
    val tobeChannel: String = config.as[String]("channel.tobe")
    val cs = settings(config)

    MqttSource
      .atMostOnce(
        cs.withClientId(deviceId),
        MqttSubscriptions(s"$prefix/$tobeChannel/$deviceId/move", MqttQoS.atLeastOnce),
        bufferSize = 1000
      )
      .alsoTo(Sink.foreach(m => println(s"${m.topic} -- ${m.payload.utf8String}")))
      .map { m =>
        m.payload.utf8String.parseJson match {
          case a: JsArray => a.convertTo[List[CellZ]]
          case o          => List(o.convertTo[CellZ])
        }
      }
      .map(e => MovementRequest(deviceId, e))
  }
}
