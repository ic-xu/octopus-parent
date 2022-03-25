package io.octopus.udp.sender;


import io.octopus.udp.message.MessageWrapper;
import io.octopus.udp.config.TransportConfig;
import io.octopus.udp.constants.UdpTransportConstants;
import io.octopus.udp.utils.ByteUtils;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;

public class UdpSendWorker implements Runnable {

    private TransportConfig config;
    private LinkedBlockingQueue<MessageWrapper> messageQueue;

    public UdpSendWorker(TransportConfig config) {
        this.config = config;
        this.messageQueue = new LinkedBlockingQueue<>();
    }

    public Boolean sendMessage(MessageWrapper message) {
        return messageQueue.add(message);
    }

    @Override
    public void run() {
        //创建channel
        DatagramChannel channel;
        try {
            channel = DatagramChannel.open();
            //指定为非阻塞方式
            channel.configureBlocking(false);
            DatagramSocket senderSocket = channel.socket();
            senderSocket.bind(new InetSocketAddress(0));
            senderSocket.setReceiveBufferSize(1024 * 1024);
            Selector selector = Selector.open();
            channel.register(selector, SelectionKey.OP_READ);
            MessageWrapper message = messageQueue.take();
            Map<String, ByteBuffer> store = new HashMap<>();
            while (true) {
                store.clear();
                try {
                    System.out.println("消息总大小为： " + message.getMessagePackage().length);
                    List<byte[]> copy = ByteUtils.copy(message.getMessagePackage(),
                            config.getIntegerProperties(UdpTransportConstants.udpTransportSegmentSize,
                                    UdpTransportConstants.defaultSegmentSize));

                    int messageHeader = message.getMessagePackage().length << 1;
                    long messageId = message.getMessageId();
//                    System.err.println("分组大小为："+copy.size());
                    //读缓冲
                    ByteBuffer readBuffer = ByteBuffer.allocate(11);
                    for (short i = 0; i < copy.size(); i++) {
//                    System.out.println("消息序号 为：" + i);
                        ByteBuffer buffer = ByteBuffer.allocate(copy.get(i).length + 14);
                        //写入消息类型和消息大小
                        buffer.putInt(messageHeader);
                        //写入消息序列号
                        buffer.putShort(i);
                        //写入消息ID
                        buffer.putLong(messageId);
                        //写入消息内容
                        buffer.put(copy.get(i));
//                    datagramPacket1.setData(buffer.array(),0,buffer.array().length);
                        buffer.rewind();
                        store.put(i + "", buffer);
                        channel.send(buffer, message.getDescInetAddress());

                        if (selector.select(1) > 0) {
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
                                    if (readBuffer.get(0) == 1 && readBuffer.getLong(3) == messageId) {
                                        int aChar = readBuffer.getChar(1);
                                        store.remove(aChar + "");
                                    }
                                }
                            }
                        }
                    }


                    int trySendCount = 0;
                    exit:
                    while (true) {
                        if (store.size() == 0) {
                            break exit;
                        }
                        trySendCount++;
                        if (selector.select(config.getIntegerProperties(UdpTransportConstants.udpTransportSegmentSendTimeOut, 20)) < 1) {
                            if (trySendCount > config.getIntegerProperties(UdpTransportConstants.udpTransportSegmentSendTimeOutCount, 3)) {
                                throw new RuntimeException(message + " send time out ..., " + message.getDescInetAddress());
                            }
                            for (String i : store.keySet()) {
                                ByteBuffer byteBuffer = store.get(i);
                                byteBuffer.rewind();
                                channel.send(byteBuffer, message.getDescInetAddress());
                            }
                            continue;
                        }
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
                                if (readBuffer.get(0) == 1 && readBuffer.getLong(3) == messageId) {
                                    int aChar = readBuffer.getChar(1);
                                    store.remove(aChar + "");
                                    if (store.size() == 0) {
                                        break exit;
                                    }
                                }
                            }
                        }
                    }

                    if (null != message.getMessageFuture()) {
                        message.getMessageFuture().onSuccess(message);
                        message = messageQueue.take();
                    }
                } catch (Exception e) {
                    //如果机器没有存活，则丢掉该消息，如果机器存活，则一值发送下去。
//                    if (null == clusterTopicRouter || !clusterTopicRouter.checkIpExit(message.getDescInetAddress())) {
                    if (null != message.getMessageFuture()) {
                        message.getMessageFuture().onError(e);
                    }
                    message = messageQueue.take();
//                    }
                }
            }

        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }

}
