package io.octopus.udp.receiver.nio;

import io.netty.buffer.ByteBuf;
import io.octopus.udp.message.DelayMessage;
import io.octopus.udp.message.MessageReceiverListener;
import io.octopus.udp.config.TransportConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.DatagramSocket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReentrantLock;

public class Receiver {
    Logger logger = LoggerFactory.getLogger(this.getClass().getName());
    private ExecutorService executorService;
    private Integer receiverPoolSize;
    private volatile DatagramSocket datagramSocket;
    private ConcurrentHashMap<Long, ByteBuf[]> messageCache = new ConcurrentHashMap<>(128);
    private DelayQueue<DelayMessage> delayMessageQueue = new DelayQueue<>();
    private MessageReceiverListener messageReceiverListener;
    private final ReentrantLock reentrantLock = new ReentrantLock();

    public Receiver(int receiverPoolSize, int listenerPort, MessageReceiverListener messageReceiverListener) throws SocketException {
        datagramSocket = new DatagramSocket(listenerPort);
//        datagramSocket.bind(new InetSocketAddress(HostUtils.getPath(), listenerPort));
        this.executorService = Executors.newFixedThreadPool(receiverPoolSize);
        this.receiverPoolSize = receiverPoolSize;
        this.messageReceiverListener = messageReceiverListener;
    }

    public void start() {
        for (int i = 0; i < receiverPoolSize; i++) {
            executorService.execute(new ReceiverPool2(reentrantLock,new TransportConfig(), datagramSocket, messageCache, delayMessageQueue, messageReceiverListener));
        }
    }


    public static void main(String[] args) throws Exception {
//        DatagramSocket datagramSocket = new DatagramSocket(1883);
//        RandomAccessFile file = new RandomAccessFile("/Users/user/workspace/Java/octopus/octopus.log", "rw");

//        ExecutorService executorService = Executors.newFixedThreadPool(5);

        Receiver receiver = new Receiver(10, 1883, (messageId, msg) -> {
            System.out.println("消息ID为：" + messageId);
            System.out.println("消息体：" + msg.toString(StandardCharsets.UTF_8));
//            executorService.execute(new Work(file, msg.array()));
            return true;
        });
        receiver.start();
    }

}
