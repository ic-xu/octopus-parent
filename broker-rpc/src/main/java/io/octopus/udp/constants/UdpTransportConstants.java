package io.octopus.udp.constants;

public interface UdpTransportConstants {
    Integer DEFAULT_SEGMENT_SIZE =512;
    String UDP_TRANSPORT_SENDER_POOL_SIZE ="udp.transport.sender-pool-size";
    String UDP_TRANSPORT_SEGMENT_SIZE ="udp.transport.segment-size";
    String UDP_TRANSPORT_SEGMENT_SEND_TIME_OUT ="udp.transport.segment.send-time-out";
    String UDP_TRANSPORT_SEGMENT_SEND_TIME_OUT_COUNT ="udp.transport.segment.send-time-out-count";
    String UDP_TRANSPORT_MESSAGE_RECEIVER_TIME_OUT ="udp.transport.message.receiver-time-out";
    Long DPP_TRANSPORT_MESSAGE_RECEIVER_TIME_OUT_DEFAULT_VALUES = 5000L;
}
