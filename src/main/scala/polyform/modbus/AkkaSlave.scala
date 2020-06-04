package polyform.modbus

import akka.actor.ActorRef
import akka.pattern.ask
import akka.util.Timeout
import com.digitalpetri.modbus.ExceptionCode
import com.digitalpetri.modbus.responses.{ReadInputRegistersResponse, WriteSingleRegisterResponse}
import com.digitalpetri.modbus.slave.{ModbusTcpSlave, ModbusTcpSlaveConfig, ServiceRequestHandler}
import io.netty.buffer.PooledByteBufAllocator
import io.netty.util.ReferenceCountUtil
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
      override def onReadInputRegisters(svc: ReadInputRegisters) {
        val request = svc.getRequest
        val addr = request.getAddress
        val f = vr ? MemoryVR.RequestVR(addr, request.getQuantity)
        f.mapTo[Seq[VR]].onComplete {
          case Success(v) =>
            val registers = PooledByteBufAllocator.DEFAULT.buffer(request.getQuantity)
            v.foreach(vv => registers.writeShort(vv.value))
            svc.sendResponse(new ReadInputRegistersResponse(registers))
            ReferenceCountUtil.release(request)
          case Failure(_) =>
            svc.sendException(ExceptionCode.SlaveDeviceFailure)
        }
      }

      override def onWriteSingleRegister(svc: WriteInputRegisters) {
        val request = svc.getRequest
        val address = request.getAddress
        val value = request.getValue
        val f = vr ? MemoryVR.ModifyVR(address, value)
        f.mapTo[VR].onComplete {
          case Success(_) =>
            svc.sendResponse(new WriteSingleRegisterResponse(address, value))
            ReferenceCountUtil.release(request)
          case Failure(_) =>
            svc.sendException(ExceptionCode.SlaveDeviceFailure)
        }
      }
    })

    slave
  }
}
