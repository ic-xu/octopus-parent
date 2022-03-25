package io.octopus.udp.receiver.netty.handler;


import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.DatagramPacket;
import io.octopus.udp.config.TransportConfig;
import io.octopus.udp.constants.UdpTransportConstants;
import io.octopus.udp.message.DelayMessage;
import io.octopus.udp.message.MessageReceiverListener;
import io.octopus.udp.utils.ByteUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.locks.ReentrantLock;


public class NettyUdpDecoderServerHandler extends SimpleChannelInboundHandler<DatagramPacket> {

    Logger logger = LoggerFactory.getLogger(NettyUdpDecoderServerHandler.class);


    private ConcurrentHashMap<Long, ByteBuf[]> messageCache;
    private DelayQueue<DelayMessage> delayMessageQueue;
    private TransportConfig config;
    private MessageReceiverListener messageReceiverListener;
    private ReentrantLock reentrantLock;
    private int readByteSize;

    public NettyUdpDecoderServerHandler(final ReentrantLock reentrantLock, TransportConfig config, ConcurrentHashMap<Long, ByteBuf[]> messageCache,
                                        DelayQueue<DelayMessage> delayMessageQueue, MessageReceiverListener messageReceiverListener) {
        this.reentrantLock = reentrantLock;
        this.messageCache = messageCache;
        this.delayMessageQueue = delayMessageQueue;
        this.config = config;
        this.messageReceiverListener = messageReceiverListener;
        readByteSize = config.getIntegerProperties(UdpTransportConstants.UDP_TRANSPORT_SEGMENT_SIZE, 1024) + 14;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        super.channelActive(ctx);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext channelHandlerContext, DatagramPacket datagramPacket) throws Exception {
        ByteBuf content = datagramPacket.content();
//        System.out.println(this);
        processReceiverPaket(channelHandlerContext, content, content.readableBytes(), datagramPacket.sender());

    }


    private void sendAck(ChannelHandlerContext ctx, InetSocketAddress socketAddress, int segment, long messageId) throws IOException {
        ByteBuf response = PooledByteBufAllocator.DEFAULT.buffer(11);
        response.writeByte(1);
        response.writeShort(segment);
        response.writeLong(messageId);
        DatagramPacket datagramPacket = new DatagramPacket(response, socketAddress);
        ctx.writeAndFlush(datagramPacket);

    }

    private void processReceiverPaket(ChannelHandlerContext ctx, ByteBuf readBuffer, int length,
                                      InetSocketAddress remoteAddress) throws IOException {
        if (length < 14) {
            return;
        }
        int messageHead = readBuffer.readInt();
//        System.out.println("消息头大小为：" + messageHead);
        short segment = readBuffer.readShort();
//        System.out.println("消息序列号为: " + sqment);
        long messageId = readBuffer.readLong();
//        System.out.println("消息ID为：+" + messageId);


        int messageLength = messageHead >> 1;
        logger.info("消息总大小为：" + messageLength);
//        short segment = ByteUtils.getShort(data, 4);
//        short segment = readBuffer.getShort(4);

        Integer messageSegmentSize = config.getIntegerProperties(
                UdpTransportConstants.UDP_TRANSPORT_SEGMENT_SIZE,
                UdpTransportConstants.DEFAULT_SEGMENT_SIZE);

//        //判断只有一个包的情况下,不需要进入缓存
//        if (length - 14 >= messageLength) {
//            messageReceiverListener.onMessage(messageId, allocateByte(data, 14, length - 14));
//            sendAck(datagramPacket, segment, messageId);
//            return;
//        }

        //有多个包的情况下，需要使用本地缓存
        ByteBuf[] receiver = messageCache.computeIfAbsent(messageId, (key) -> {
            Integer segmentSize = ByteUtils.getSegmentSize(messageLength, messageSegmentSize);
            delayMessageQueue.put(new DelayMessage(key, config.getLongProperties(UdpTransportConstants.UDP_TRANSPORT_MESSAGE_RECEIVER_TIME_OUT,
                    UdpTransportConstants.DPP_TRANSPORT_MESSAGE_RECEIVER_TIME_OUT_DEFAULT_VALUES)));
            return new ByteBuf[segmentSize];
        });

        ByteBuf byteBufSegment = Unpooled.copiedBuffer(readBuffer);
        receiver[segment] = byteBufSegment;
        for (ByteBuf b : receiver) {
            if (null == b) {
                sendAck(ctx, remoteAddress, segment, messageId);
                //检测超时的消息
                cleanTimeOutMessageCache(ctx, remoteAddress, messageReceiverListener);
                return;
            }
        }

        //消息被处理成功之后才回复下一个
        if (!messageReceiverListener.onMessage(messageId, copyArray(receiver))) {
            return;
        }
        messageCache.remove(messageId);

        //检测超时的消息
        cleanTimeOutMessageCache(ctx, remoteAddress, messageReceiverListener);

        //发送ack
        sendAck(ctx, remoteAddress, segment, messageId);

    }

    private ByteBuf copyArray(ByteBuf... array) {
        return Unpooled.copiedBuffer(array);
    }

    private void cleanTimeOutMessageCache(ChannelHandlerContext ctx, InetSocketAddress socketAddress, MessageReceiverListener messageReceiverListener) throws IOException {
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
            sendAck(ctx, socketAddress, remove.length - 1, message.getMessageId());

        }
    }


    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        System.err.println(cause.getMessage());
    }


}
