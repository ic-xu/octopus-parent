package io.octopus.scala.broker.session

class SessionStatus extends Enumeration {
  type SessionStatus = Value
  var CONNECTED, CONNECTING, DISCONNECTING, DISCONNECTED = Value
}
