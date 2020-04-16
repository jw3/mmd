package polyform

import com.digitalpetri.modbus.requests.ReadInputRegistersRequest
import com.digitalpetri.modbus.responses.ReadInputRegistersResponse
import com.digitalpetri.modbus.slave.ServiceRequestHandler.ServiceRequest

package object modbus {
  type ReadInputRegisters = ServiceRequest[ReadInputRegistersRequest, ReadInputRegistersResponse]
}
