package io.octopus.udp.sender;

import io.octopus.udp.message.MessageSendListener;
import io.octopus.udp.message.MessageWrapper;
import io.octopus.udp.config.TransportConfig;
import io.octopus.udp.constants.UdpTransportConstants;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class UdpSenderManager {

    private UdpSender[] udpSenders;
    private TransportConfig config;

    private ReentrantLock reentrantLock;

    private Condition condition;

    private AtomicLong messageQueueSize = new AtomicLong(0);


    public UdpSenderManager(TransportConfig config) throws IOException {
        this.config = config;
        Integer sendPoolSize = config.getIntegerProperties(UdpTransportConstants.UDP_TRANSPORT_SENDER_POOL_SIZE, 1);
        if (sendPoolSize <= 1) {
            udpSenders = new UdpSender[1];
        } else {
            udpSenders = new UdpSender[sendPoolSize];
        }
        for (int i = 0; i < udpSenders.length; i++) {
            udpSenders[i] = new UdpSender(config, messageQueueSize);
        }
        reentrantLock = new ReentrantLock();
        condition = reentrantLock.newCondition();

        Thread thread = new Thread(() -> {
            while (true) {
                if (messageQueueSize.get() > 0) {
                    for (int i = 0; i < udpSenders.length; i++) {
                        udpSenders[i].doLoop();
                    }
                }else{
                    reentrantLock.lock();
                    try {
                        condition.await();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }finally {
                        reentrantLock.unlock();
                    }
                }
            }

        });
        thread.setName("udp sender worker");
        thread.start();
    }

    public Boolean sendUdpMessage(byte[] message, Long messageId, MessageSendListener listener, InetSocketAddress inetAddress) {
        return sendUdpMessage(new MessageWrapper(message, messageId, listener, inetAddress));
    }

    public Boolean sendUdpMessage(MessageWrapper message) {
        messageQueueSize.incrementAndGet();
        reentrantLock.lock();
        try {
            condition.signal();
        }finally {
            reentrantLock.unlock();
        }
        int i = new Random().nextInt(udpSenders.length);
        return udpSenders[i].sendMessage(message);
    }

}
