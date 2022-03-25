package io.octopus.udp.constants;

public interface UdpTransportConstants {
    Integer defaultSegmentSize=512;
    String udpTransportSenderPoolSize="udp.transport.sender-pool-size";
    String udpTransportSegmentSize="udp.transport.segment-size";
    String udpTransportSegmentSendTimeOut="udp.transport.segment.send-time-out";
    String udpTransportSegmentSendTimeOutCount="udp.transport.segment.send-time-out-count";
    String dppTransportMessageReceiverTimeOut="udp.transport.message.receiver-time-out";
    Long dppTransportMessageReceiverTimeOutDefaultValues = 5000L;
}
