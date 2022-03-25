package io.octopus.udp.receiver.nio;


import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.octopus.udp.message.DelayMessage;
import io.octopus.udp.message.MessageReceiverListener;
import io.octopus.udp.config.TransportConfig;
import io.octopus.udp.constants.UdpTransportConstants;
import io.octopus.udp.utils.ByteUtils;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.DelayQueue;

public class ReceiverPool implements Runnable{

    private static Charset charset = StandardCharsets.UTF_8;

    private  ConcurrentHashMap<Long, byte[][]> messageCache ;
    private  DelayQueue<DelayMessage> delayMessageQueue ;
    private TransportConfig config;
    private MessageReceiverListener messageReceiverListener;
    byte[] readByte ;
    private volatile DatagramSocket datagramSocket;


    public ReceiverPool(TransportConfig config, DatagramSocket datagramSocket,
                        ConcurrentHashMap<Long, byte[][]> messageCache,
                        DelayQueue<DelayMessage> delayMessageQueue,
                        MessageReceiverListener messageReceiverListener) {
        this.messageCache = messageCache;
        this.delayMessageQueue =delayMessageQueue;
        this.config = config;
        this.messageReceiverListener = messageReceiverListener;
        this.datagramSocket = datagramSocket;
        int size = config.getIntegerProperties(UdpTransportConstants.UDP_TRANSPORT_SEGMENT_SIZE,1024)+14;
        readByte= new byte[size];
    }


    private void udp() throws IOException {
        DatagramPacket datagramPacket = new DatagramPacket(readByte, 0, readByte.length);
        datagramSocket.receive(datagramPacket);
        byte[] data = datagramPacket.getData();
//        System.out.println("数组大小：" + data.length);
        int length = datagramPacket.getLength();

        if (length < 14) {
            return;
        }
//        System.out.println("包大小：" +length);
        int messageHead = ByteUtils.getInt(data, 0);
        int messageLength = messageHead >> 1;
//        System.out.println("消息总大小为：" + messageLength);
        short segment = ByteUtils.getShort(data, 4);
//        System.out.println("消息序列号为: " + segment);
        long messageId = ByteUtils.getLong(data, 6);

        Integer messageSegmentSize = config.getIntegerProperties(
                UdpTransportConstants.UDP_TRANSPORT_SEGMENT_SIZE,
                UdpTransportConstants.DEFAULT_SEGMENT_SIZE);
        if ((segment + 1) * messageSegmentSize >= messageLength) {
            if (segment == 0) {
                messageReceiverListener.onMessage(messageId, Unpooled.wrappedBuffer(Arrays.copyOfRange(data, 14, length)));
            } else {
                byte[][] cache = messageCache.get(messageId);
                if (null != cache) {
                    ByteBuffer byteBuffer = ByteBuffer.allocateDirect(messageLength);
                    byte[] receiver = new byte[messageLength];
                    for (int i = 0; i < cache.length - 1; i++) {
                        byteBuffer.put(cache[i]);
                    }
                    byteBuffer.put(data, 14, length - 14);
//                    cache[segment] = byteBuffer.array();
                    byteBuffer.flip();
                    byteBuffer.get(receiver);
                    messageReceiverListener.onMessage(messageId, Unpooled.wrappedBuffer(receiver));
                    messageCache.remove(messageId);
                    cleanTimeOutMessageCache();
                }
            }

        } else {
            byte[][] bytes = messageCache.computeIfAbsent(messageId, (key) -> {
                Integer segmentSize = ByteUtils.getSegmentSize(messageLength, messageSegmentSize);
                delayMessageQueue.put(new DelayMessage(key,
                        config.getLongProperties(UdpTransportConstants.UDP_TRANSPORT_MESSAGE_RECEIVER_TIME_OUT,
                                UdpTransportConstants.DPP_TRANSPORT_MESSAGE_RECEIVER_TIME_OUT_DEFAULT_VALUES)));
//                ByteBuf buf = Unpooled.copiedBuffer()
                return new byte[segmentSize][];
            });
            byte[] bytes1 = Arrays.copyOfRange(data, 14, length);
            bytes[segment] = bytes1;
            messageCache.put(messageId, bytes);
        }


//        System.out.println(datagramPacket.getSocketAddress());
        ByteBuf response = Unpooled.buffer(11);
        response.writeByte(1);
        response.writeShort(segment);
        response.writeLong(messageId);

        datagramPacket.setData(response.array());
        datagramPacket.setLength(11);
        datagramSocket.send(datagramPacket);
    }

    private void cleanTimeOutMessageCache(){
        List<DelayMessage> messageList = new ArrayList<>();
        delayMessageQueue.drainTo(messageList);
        for (DelayMessage message:messageList) {
            messageCache.remove(message.getMessageId());
        }
    }



    private static void nioUdp() throws Exception {
        //创建channel
        DatagramChannel channel = DatagramChannel.open();
        //指定为非阻塞方式
        channel.configureBlocking(false);
        DatagramSocket socket = channel.socket();
        //绑定ip和端口
        InetSocketAddress address = new InetSocketAddress(1883);
        socket.bind(address);

        //创建监听器
        Selector selector = Selector.open();
        //注册读事件
        channel.register(selector, SelectionKey.OP_READ);

        //记录前一客户端地址，用于新起发送线程，仅示例，实际中用map等方式标记
        String preClientAddress = "";

        //读缓冲
        ByteBuffer readBuffer = ByteBuffer.allocate(1024);
        while (true) {
            //等事件出现
            if (selector.select() < 1) {
                continue;
            }

            //获取发生的事件
            Set<SelectionKey> keys = selector.selectedKeys();
            Iterator<SelectionKey> it = keys.iterator();
            while (it.hasNext()) {
                //获取事件，移除正在处理的事件
                SelectionKey key = it.next();
                it.remove();

                //读取消息
                if (key.isReadable()) {
                    DatagramChannel datagramChannel = (DatagramChannel) key.channel();
                    readBuffer.clear();
                    datagramChannel.receive(readBuffer);
                    readBuffer.flip();

                    int messageHead = readBuffer.getInt();
                    System.out.println("消息头大小为：" + messageHead);
                    short sqment = readBuffer.getShort();
                    System.out.println("消息序列号为: " + sqment);
                    long messageId = readBuffer.getLong();
                    System.out.println("消息ID为：+" + messageId);


//                    byte[] messageBody = new byte[content.readableBytes()];
//                    content.readBytes(messageBody);
//                    System.out.println("消息体：" + new String(messageBody, StandardCharsets.UTF_8));


//                    ByteBuffer response = ByteBuffer.allocate(11);
////        ByteBuf response = Unpooled.buffer(11);
//
//                    response.put((byte)0x01);
//                    response.putShort(sqment);
//                    response.putLong(messageId);
//                    response.flip();


//                    //新建发送消息线程
//                    if (!preClientAddress.equals(sa.toString())) {
////                        new WriteThread(channel, sa).start();
//                        preClientAddress = sa.toString();
//                    }
//                    readBuffer.flip();
                    String msg = charset.decode(readBuffer).toString();
                    System.out.println("消息体：" + msg);
//                    System.out.println("server receive msg : " + msg);

                }
            }

        }
    }

    @Override
    public void run() {
        while (true){
            try {
                udp();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
