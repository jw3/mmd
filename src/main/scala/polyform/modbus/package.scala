package polyform

import com.digitalpetri.modbus.requests.{ReadInputRegistersRequest, WriteSingleRegisterRequest}
import com.digitalpetri.modbus.responses.{ReadInputRegistersResponse, WriteSingleRegisterResponse}
import com.digitalpetri.modbus.slave.ServiceRequestHandler.ServiceRequest

package object modbus {
  type ReadInputRegisters = ServiceRequest[ReadInputRegistersRequest, ReadInputRegistersResponse]
  type WriteInputRegisters = ServiceRequest[WriteSingleRegisterRequest, WriteSingleRegisterResponse]
}
