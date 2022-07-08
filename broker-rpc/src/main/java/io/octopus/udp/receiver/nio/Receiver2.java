package io.octopus.udp.receiver.nio;

import io.netty.buffer.ByteBuf;
import io.netty.util.concurrent.DefaultThreadFactory;
import io.octopus.kernel.utils.HostUtils;
import io.octopus.udp.config.TransportConfig;
import io.octopus.udp.message.DelayMessage;
import io.octopus.udp.message.MessageReceiverListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;

public class Receiver2 {
    Logger logger = LoggerFactory.getLogger(this.getClass().getName());
    private ExecutorService executorService;
    private Integer receiverPoolSize;
    //    private volatile DatagramSocket datagramSocket;
    private ConcurrentHashMap<Long, ByteBuf[]> messageCache = new ConcurrentHashMap<>(128);
    private DelayQueue<DelayMessage> delayMessageQueue = new DelayQueue<>();
    private MessageReceiverListener messageReceiverListener;
    private final ReentrantLock reentrantLock = new ReentrantLock();
    private DatagramChannel datagramChannel;
    private Selector selector;

    public Receiver2(int receiverPoolSize, int listenerPort, MessageReceiverListener messageReceiverListener) throws IOException {
        // 获取一个DatagramChannel，并设置为非阻塞模式,绑定ip和port
        datagramChannel = DatagramChannel.open();
        datagramChannel.configureBlocking(false);
        InetSocketAddress inetSocketAddress = new InetSocketAddress(Objects.requireNonNull(HostUtils.getAnyIpv4Address()), listenerPort);
        datagramChannel.bind(inetSocketAddress);
//        datagramChannel.connect(inetSocketAddress);
        // 获取注册 Selector，并将 datagramChannel 的读事件（不需要连接）通道注册到 Selector
        selector = Selector.open();
        datagramChannel.register(selector, SelectionKey.OP_READ);


//        datagramSocket = new DatagramSocket(listenerPort);
//        datagramSocket.setSoTimeout(5000);
//        datagramSocket.bind(new InetSocketAddress(HostUtils.getPath(), listenerPort));
        this.executorService = Executors.newFixedThreadPool(receiverPoolSize,new DefaultThreadFactory("receiverPool"));
        this.receiverPoolSize = receiverPoolSize;
        this.messageReceiverListener = messageReceiverListener;
    }

    public void start() {
        for (int i = 0; i < receiverPoolSize; i++) {
            ReceiverPool2Nio receiverPool2Nio = new ReceiverPool2Nio(reentrantLock,
                new TransportConfig(), datagramChannel, selector, messageCache,
                delayMessageQueue, messageReceiverListener);
//            receiverPool2Nio.run();
            Thread thread = new Thread(receiverPool2Nio);
            thread.setDaemon(true);
            thread.setName("receiverPool2Nio"+i);
            thread.start();
        }
    }


    public static void main(String[] args) throws Exception {
//        DatagramSocket datagramSocket = new DatagramSocket(1883);
//        RandomAccessFile file = new RandomAccessFile("/Users/user/workspace/Java/octopus/octopus.log", "rw");

//        ExecutorService executorService = Executors.newFixedThreadPool(5);

        Receiver2 receiver = new Receiver2(1, 1883, (messageId, msg) -> {
//            System.out.println("消息ID为：" + messageId);
            System.out.println(msg.toString(StandardCharsets.UTF_8));
//            executorService.execute(new Work(file, msg.array()));
            return true;
        });
        receiver.start();
        TimeUnit.HOURS.sleep(1);

    }
}
