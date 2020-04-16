package polyform.modbus

import akka.actor.ActorRef
import akka.pattern.ask
import akka.util.Timeout
import com.digitalpetri.modbus.ExceptionCode
import com.digitalpetri.modbus.responses.ReadInputRegistersResponse
import com.digitalpetri.modbus.slave.{ModbusTcpSlave, ModbusTcpSlaveConfig, ServiceRequestHandler}
import io.netty.buffer.PooledByteBufAllocator
import io.netty.util.ReferenceCountUtil
import polyform.api
import polyform.modbus.MemoryVR.VR

// todo;; proper ec
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.DurationInt
import scala.util.{Failure, Success}

object AkkaSlave {
  private implicit val askTimeout: Timeout = Timeout(2.seconds)

  def apply(vr: ActorRef, config: ModbusTcpSlaveConfig): ModbusTcpSlave = {
    val slave: ModbusTcpSlave = new ModbusTcpSlave(config)

    slave.setRequestHandler(new ServiceRequestHandler {
      override def onReadInputRegisters(svc: api.ReadInputRegisters) {
        val request = svc.getRequest
        val addr = request.getAddress
        val f = vr ? MemoryVR.RequestVR(addr)
        f.mapTo[VR].onComplete {
          case Success(v) =>
            val registers = PooledByteBufAllocator.DEFAULT.buffer(request.getQuantity)
            registers.writeShort(v.data)
            svc.sendResponse(new ReadInputRegistersResponse(registers))
            ReferenceCountUtil.release(request)
          case Failure(_) =>
            svc.sendException(ExceptionCode.SlaveDeviceFailure)
        }
      }
    })

    slave
  }
}
