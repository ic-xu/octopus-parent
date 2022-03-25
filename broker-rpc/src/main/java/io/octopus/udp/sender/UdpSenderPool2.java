package io.octopus.udp.sender;


import io.netty.util.concurrent.DefaultThreadFactory;
import io.octopus.udp.message.DatagramSocketWrapper;
import io.octopus.udp.message.MessageSendListener;
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
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class UdpSenderPool2 {
    private ExecutorService executor;
    private ArrayBlockingQueue<DatagramSocketWrapper> senderSocketPool;
    private TransportConfig config;

    public UdpSenderPool2(TransportConfig config) throws IOException {
        this.config = config;
        Integer sendPoolSize = config.getIntegerProperties(UdpTransportConstants.UDP_TRANSPORT_SENDER_POOL_SIZE, 1);
        if (sendPoolSize <= 1) {
            executor = Executors.newSingleThreadExecutor();
            senderSocketPool = new ArrayBlockingQueue<>(1);
            //创建channel
            DatagramChannel channel = DatagramChannel.open();
            //指定为非阻塞方式
            channel.configureBlocking(false);
            DatagramSocket senderSocket = channel.socket();
            senderSocket.bind(new InetSocketAddress(0));
            senderSocket.setReceiveBufferSize(1024*1024);
            senderSocketPool.add(new DatagramSocketWrapper(senderSocket,Selector.open()));
        } else {
            executor = Executors.newFixedThreadPool(sendPoolSize, new DefaultThreadFactory("sender thread"));
            senderSocketPool = new ArrayBlockingQueue<>(sendPoolSize);
            for (int i = 0; i < sendPoolSize; i++) {
                //创建channel
                DatagramChannel channel = DatagramChannel.open();
                //指定为非阻塞方式
                channel.configureBlocking(false);
                DatagramSocket senderSocket = channel.socket();
                senderSocket.bind(new InetSocketAddress(0));
                senderSocket.setReceiveBufferSize(1024*1024);
                senderSocketPool.add(new DatagramSocketWrapper(senderSocket,Selector.open()));
            }
        }


    }


    public void sendUdpMessage(byte[] message, Long messageId, MessageSendListener listener, InetSocketAddress inetAddress) {
        executor.execute(new UdpSendWorker(new MessageWrapper(message, messageId, listener, inetAddress), config));
    }



    class UdpSendWorker implements Runnable {

        private MessageWrapper messageWrapper;
        private TransportConfig config;

        public UdpSendWorker(MessageWrapper messageWrapper, TransportConfig config ) {
            this.messageWrapper = messageWrapper;
            this.config = config;
        }

        @Override
        public void run() {
            DatagramSocketWrapper datagramSocketWrapper = senderSocketPool.poll();
            DatagramSocket senderSocket =datagramSocketWrapper.getSocket();
            Selector selector = datagramSocketWrapper.getSelector();
            try {
                DatagramChannel channel = senderSocket.getChannel();
                channel.register(selector, SelectionKey.OP_READ);

                List<byte[]> copy = ByteUtils.copy(messageWrapper.getMessagePackage(),
                        config.getIntegerProperties(UdpTransportConstants.UDP_TRANSPORT_SEGMENT_SIZE,
                                UdpTransportConstants.DEFAULT_SEGMENT_SIZE));
//
//                int messageType = 0;//0 表示发送，1表示响应。
                int messageHeader = messageWrapper.getMessagePackage().length << 1;
                long messageId = messageWrapper.getMessageId();

//                System.out.println("消息头为：" + messageHeader);
//                System.out.println("消息大小为：" + (messageHeader >> 1));
//                System.out.println("消息ID 为：" + messageId);

                Map<String,ByteBuffer> store = new HashMap<>();

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
                    buffer.flip();
                    store.put(i+"",buffer);
                    channel.send(buffer, messageWrapper.getDescInetAddress());

                    if(selector.selectNow()>0){
                        Iterator<SelectionKey> it = selector.selectedKeys().iterator();
                        while (it.hasNext()) {
                            SelectionKey nextKey = it.next();
                            DatagramSocket socket = ((DatagramChannel) nextKey.channel()).socket();
                            it.remove();
                            if(socket.getLocalPort()!=senderSocket.getLocalPort()){
                                continue ;
                            }
                            if (nextKey.isReadable()) {
                                DatagramChannel datagramChannel = (DatagramChannel) nextKey.channel();
                                readBuffer.clear();
                                datagramChannel.receive(readBuffer);
                                if (readBuffer.get(0) == 1 && readBuffer.getLong(3)== messageId) {
                                    int aChar = readBuffer.getChar(1);
                                    store.remove(aChar+"");
                                }
                            }
                        }
                    }
                }


                int trySendCount = 0;
                exit:
                while (true) {
                    if(store.size()==0){
                        break exit;
                    }
                    trySendCount++;
                    if (selector.select(config.getIntegerProperties(UdpTransportConstants.UDP_TRANSPORT_SEGMENT_SEND_TIME_OUT,1000)) < 1) {
                        if (trySendCount > config.getIntegerProperties(
                                UdpTransportConstants.UDP_TRANSPORT_SEGMENT_SEND_TIME_OUT_COUNT,3)) {
                            throw new RuntimeException("send message time out");
                        }
                        for (String i:store.keySet()) {
                            channel.send(store.get(i), messageWrapper.getDescInetAddress());
                        }
//                        channel.send(buffer, messageWrapper.getDescInetAddress());
                        continue;
                    }
                    Iterator<SelectionKey> it = selector.selectedKeys().iterator();
                    while (it.hasNext()) {
                        SelectionKey nextKey = it.next();
                        DatagramSocket socket = ((DatagramChannel) nextKey.channel()).socket();
                        it.remove();
                        if(socket.getLocalPort()!=senderSocket.getLocalPort()){
                            continue ;
                        }
                        if (nextKey.isReadable()) {
                            DatagramChannel datagramChannel = (DatagramChannel) nextKey.channel();
                            readBuffer.clear();
                            datagramChannel.receive(readBuffer);
                            if (readBuffer.get(0) == 1 && readBuffer.getLong(3)== messageId) {
                                int aChar = readBuffer.getChar(1);
                                store.remove(aChar+"");
                                if(store.size()==0){
                                    break exit;
                                }
                            }
                        }
                    }
                }

                if (null != messageWrapper.getMessageFuture())
                    messageWrapper.getMessageFuture().onSuccess(messageWrapper);
            } catch (Exception e) {
                if (null != messageWrapper.getMessageFuture())
                    messageWrapper.getMessageFuture().onError(e);
                e.printStackTrace();
            } finally {
                senderSocketPool.add(datagramSocketWrapper);
            }

        }

    }

}
