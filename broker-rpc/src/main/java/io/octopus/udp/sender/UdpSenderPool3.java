package io.octopus.udp.sender;


import io.octopus.udp.message.MessageSendListener;
import io.octopus.udp.message.MessageWrapper;
import io.octopus.udp.config.TransportConfig;
import io.octopus.udp.constants.UdpTransportConstants;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Random;

public class UdpSenderPool3 {

    private UdpSendWorker[] workers;
    private TransportConfig config;

    public UdpSenderPool3(TransportConfig config) throws IOException {
        this.config = config;
        Integer sendPoolSize = config.getIntegerProperties(UdpTransportConstants.UDP_TRANSPORT_SENDER_POOL_SIZE, 1);
        if (sendPoolSize <= 1) {
            workers = new UdpSendWorker[1];
        } else {
            workers = new UdpSendWorker[sendPoolSize];
        }
        for (int i = 0; i < workers.length; i++) {
            workers[i] = new UdpSendWorker(config);
            Thread thread = new Thread(workers[i]);
            thread.setName("udp sender "+i);
            thread.setDaemon(true);
            thread.start();
        }
    }


    public Boolean sendUdpMessage(byte[] message, Long messageId, MessageSendListener listener, InetSocketAddress inetAddress) {
        int i = new Random().nextInt(workers.length);
        return workers[i].sendMessage(new MessageWrapper(message, messageId, listener, inetAddress));
    }


}
