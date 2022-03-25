package io.octopus.udp.sender;

import io.octopus.udp.message.MessageWrapper;
import io.octopus.udp.config.TransportConfig;
import io.octopus.udp.constants.UdpTransportConstants;
import io.octopus.udp.utils.ByteUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.*;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;

public class UdpSender {

    Logger logger = LoggerFactory.getLogger(UdpSender.class);

    private TransportConfig config;
    private Queue<MessageWrapper> messageQueue;
    private Map<Short, ByteBuffer> store = new HashMap<>();
    private DatagramChannel channel;
    private ByteBuffer readBuffer = ByteBuffer.allocate(11);
    private Selector selector;
    private DatagramSocket senderSocket;
    private volatile MessageWrapper concurrentMessage;
    private Long lastSendPackageTime;
    private AtomicLong atomicLong;

    //尝试发送次数
    private int retrySendCount = 0;
    //是否重新发送
    private boolean isRetry = false;

    public UdpSender(TransportConfig config,AtomicLong atomicLong) throws IOException {
        this.config = config;
        this.messageQueue = new LinkedBlockingQueue<>();
        this.atomicLong = atomicLong;
        init();
    }

    public Boolean sendMessage(MessageWrapper message) {
        return messageQueue.add(message);
    }

    private void init() throws IOException {
        //创建channel
        channel = DatagramChannel.open();
        //指定为非阻塞方式
        channel.configureBlocking(false);
        senderSocket = channel.socket();
        senderSocket.bind(new InetSocketAddress(0));
        senderSocket.setReceiveBufferSize(1024 * 1024);
        selector = Selector.open();
        channel.register(selector, SelectionKey.OP_READ);
    }

    public synchronized void doLoop() {
        doSend();
        if (null != concurrentMessage)
            doReceiver();
    }

    private void success(MessageWrapper messageWrapper){
        atomicLong.decrementAndGet();
        concurrentMessage.getMessageFuture().onSuccess(concurrentMessage);
    }

    private void error(Exception e){
        atomicLong.decrementAndGet();
        concurrentMessage.getMessageFuture().onError(e);
    }
    /**
     * 发送方法
     */
    private void doSend() {
        try {
            if (null != concurrentMessage) {
                if (retrySendCount > 3) {
                    error(new RuntimeException("send time out ...."));
                    concurrentMessage = null;
                } else if (isRetry) {
                    isRetry = false;
                    System.out.println();
                    logger.debug("尝试发送第 {} 次发送", retrySendCount);
                    for (ByteBuffer buffer : store.values()) {
                        channel.send(buffer, concurrentMessage.getDescInetAddress());
                        lastSendPackageTime = System.currentTimeMillis();
                    }
                }
            } else {
                //状态归0
                retrySendCount=0;
                //重试改为false
                isRetry = false;

                concurrentMessage = messageQueue.poll();
                if (null == concurrentMessage)
                    return;
                List<byte[]> copy = ByteUtils.copy(concurrentMessage.getMessagePackage(),
                        config.getIntegerProperties(UdpTransportConstants.udpTransportSegmentSize,
                                UdpTransportConstants.defaultSegmentSize));

                int messageHeader = concurrentMessage.getMessagePackage().length << 1;
                long messageId = concurrentMessage.getMessageId();

                for (short i = 0; i < copy.size(); i++) {
                    logger.debug("消息序号为 {}",i);
                    ByteBuffer buffer = ByteBuffer.allocate(copy.get(i).length + 14);

                    //写入消息类型和消息大小
                    buffer.putInt(messageHeader);
                    //写入消息序列号
                    buffer.putShort(i);
                    //写入消息ID
                    buffer.putLong(messageId);
                    //写入消息内容
                    buffer.put(copy.get(i));
                    // datagramPacket1.setData(buffer.array(),0,buffer.array().length);
                    buffer.rewind();
                    store.put(i, buffer);

                    channel.send(buffer, concurrentMessage.getDescInetAddress());
                    lastSendPackageTime = System.currentTimeMillis();
                }
                doReceiver();

            }
        } catch (IOException e) {
            e.printStackTrace();
        }

    }


    private void doReceiver() {
        try {
            if (selector.selectNow() > 0) {
                Iterator<SelectionKey> it = selector.selectedKeys().iterator();
                while (it.hasNext()) {
                    SelectionKey nextKey = it.next();
                    DatagramSocket socket = ((DatagramChannel) nextKey.channel()).socket();
                    if (socket.getLocalPort() != senderSocket.getLocalPort()) {
                        continue;
                    }
                    it.remove();
                    if (nextKey.isReadable()) {
                        DatagramChannel datagramChannel = (DatagramChannel) nextKey.channel();
                        readBuffer.clear();
                        datagramChannel.receive(readBuffer);
                        if (readBuffer.get(0) == 1 && readBuffer.getLong(3) == concurrentMessage.getMessageId()) {
                            Short segment = readBuffer.getShort(1);
                            store.remove(segment);
                        }
                    }
                }
            }

            if (store.size() == 0 || (System.currentTimeMillis() - lastSendPackageTime > 1000)) {
                if (store.size() == 0) {
                    success(concurrentMessage);
                    concurrentMessage = null;
                } else {
                    //尝试次数加一
                    retrySendCount++;

                    //重新发送
                    isRetry = true;
                }

//                //最后执行
//                this.status.compareAndSet(false, true);

            }

        } catch (IOException e) {
            e.printStackTrace();
        }

    }


}
