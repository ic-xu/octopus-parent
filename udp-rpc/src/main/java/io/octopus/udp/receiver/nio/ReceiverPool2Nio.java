package io.octopus.udp.receiver.nio;


import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.octopus.udp.message.DelayMessage;
import io.octopus.udp.message.MessageReceiverListener;
import io.octopus.udp.config.TransportConfig;
import io.octopus.udp.constants.UdpTransportConstants;
import io.octopus.udp.utils.ByteUtils;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.locks.ReentrantLock;

public class ReceiverPool2Nio implements Runnable {

    private ConcurrentHashMap<Long, ByteBuf[]> messageCache;
    private DelayQueue<DelayMessage> delayMessageQueue;
    private TransportConfig config;
    private MessageReceiverListener messageReceiverListener;

    private final DatagramChannel datagramChannel;
    private final ReentrantLock reentrantLock;
    private final Selector selector;
    private int readByteSize;
    private final ByteBuffer response = ByteBuffer.allocate(11);


    public ReceiverPool2Nio(final ReentrantLock reentrantLock, TransportConfig config, DatagramChannel datagramChannel, Selector selector,
                            ConcurrentHashMap<Long, ByteBuf[]> messageCache,
                            DelayQueue<DelayMessage> delayMessageQueue,
                            MessageReceiverListener messageReceiverListener) {
        this.reentrantLock = reentrantLock;
        this.messageCache = messageCache;
        this.delayMessageQueue = delayMessageQueue;
        this.config = config;
        this.messageReceiverListener = messageReceiverListener;
        this.datagramChannel = datagramChannel;
        this.selector = selector;
        readByteSize = config.getIntegerProperties(UdpTransportConstants.udpTransportSegmentSize, 1024) + 14;
    }


    private ByteBuf allocate(int capacity) {
        return Unpooled.directBuffer(capacity);
    }

    private ByteBuf allocateByte(ByteBuffer bytes) {
        return Unpooled.wrappedBuffer(bytes);
    }

    private ByteBuf copyArray(ByteBuf... array) {
        return Unpooled.copiedBuffer(array);
    }


    private void nioUdp() throws Exception {
        //读缓冲
         ByteBuffer readBuffer = ByteBuffer.allocateDirect(readByteSize);
        //等事件出现
        if (selector.selectNow() < 1) {
            return;
        }
        //获取发生的事件
        Set<SelectionKey> keys = selector.selectedKeys();
        Iterator<SelectionKey> it = keys.iterator();
        while (it.hasNext()) {
            //获取事件，移除正在处理的事件
            SelectionKey key = it.next();
            //读取消息
            if (key.isReadable()) {
                DatagramChannel datagramChannel = (DatagramChannel) key.channel();
//                readBuffer.clear();
                SocketAddress receiveAddress = datagramChannel.receive(readBuffer);

//                    datagramChannel.read()
                readBuffer.flip();
                processReceiverPaket(readBuffer, readBuffer.limit(), receiveAddress);
                selector.selectedKeys().remove(key);
            }
        }

    }

    private void sendAck(SocketAddress socketAddress, int segment, Long messageId) throws IOException {
        response.clear();
        response.put((byte) 1);
        response.putShort((short) segment);
        response.putLong(messageId);
        response.flip();

        datagramChannel.send(response, socketAddress);
    }

    private void processReceiverPaket(ByteBuffer readBuffer, int length, SocketAddress remoteAddress) throws IOException {
        if (length < 14) {
            return;
        }
        int messageHead = readBuffer.getInt();
//        System.out.println("消息头大小为：" + messageHead);
        short sqment = readBuffer.getShort();
//        System.out.println("消息序列号为: " + sqment);
        long messageId = readBuffer.getLong();
//        System.out.println("消息ID为：+" + messageId);


        int messageLength = messageHead >> 1;
//        System.out.println("消息总大小为：" + messageLength);
//        short segment = ByteUtils.getShort(data, 4);
        short segment = readBuffer.getShort(4);

        Integer messageSegmentSize = config.getIntegerProperties(
            UdpTransportConstants.udpTransportSegmentSize,
            UdpTransportConstants.defaultSegmentSize);

//        //判断只有一个包的情况下,不需要进入缓存
//        if (length - 14 >= messageLength) {
//            messageReceiverListener.onMessage(messageId, allocateByte(data, 14, length - 14));
//            sendAck(datagramPacket, segment, messageId);
//            return;
//        }

        //有多个包的情况下，需要使用本地缓存
        ByteBuf[] receiver = messageCache.computeIfAbsent(messageId, (key) -> {
            Integer segmentSize = ByteUtils.getSegmentSize(messageLength, messageSegmentSize);
            delayMessageQueue.put(new DelayMessage(key, config.getLongProperties(UdpTransportConstants.dppTransportMessageReceiverTimeOut,
                UdpTransportConstants.dppTransportMessageReceiverTimeOutDefaultValues)));
            return new ByteBuf[segmentSize];
        });

        ByteBuf byteBufSegment = allocateByte(readBuffer);
        receiver[segment] = byteBufSegment;
        reentrantLock.lock();
        try {
            for (ByteBuf b : receiver) {
                if (null == b) {
                    sendAck(remoteAddress, segment, messageId);
                    //检测超时的消息
                    cleanTimeOutMessageCache(remoteAddress, messageReceiverListener);
                    return;
                }
            }
        } finally {
            reentrantLock.unlock();
        }
        messageReceiverListener.onMessage(messageId, copyArray(receiver));
        messageCache.remove(messageId);

        //检测超时的消息
        cleanTimeOutMessageCache(remoteAddress, messageReceiverListener);

        //发送ack
        sendAck(remoteAddress, segment, messageId);

    }

    private void cleanTimeOutMessageCache(SocketAddress socketAddress, MessageReceiverListener messageReceiverListener) throws IOException {
        List<DelayMessage> messageList = new ArrayList<>();
        delayMessageQueue.drainTo(messageList);
        for (DelayMessage message : messageList) {
            ByteBuf[] remove = messageCache.remove(message.getMessageId());
            if (null == remove)
                return;
            for (ByteBuf b : remove) {
                if (null == b) {
                    return;
                }
            }
            //回掉ack
            messageReceiverListener.onMessage(message.getMessageId(), copyArray(remove));
            sendAck(socketAddress, remove.length - 1, message.getMessageId());
        }
    }


    @Override
    public void run() {
        while (true) {
            try {
                nioUdp();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
