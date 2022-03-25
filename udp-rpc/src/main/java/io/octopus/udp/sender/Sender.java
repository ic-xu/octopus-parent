package io.octopus.udp.sender;

import io.octopus.udp.config.TransportConfig;
import io.octopus.udp.message.MessageSendListener;
import io.octopus.udp.message.MessageWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Random;

public class Sender {

    Logger logger = LoggerFactory.getLogger(Sender.class);

    private UdpSenderManager udpSenderManager;


    public Sender() throws IOException {
        udpSenderManager = new UdpSenderManager(new TransportConfig());
    }

    public Sender(TransportConfig transportConfig) throws IOException {
        udpSenderManager = new UdpSenderManager(transportConfig);

    }


    public void sendMessageNotResult(byte[] byteBuf, Long messageTranceId, InetSocketAddress descInetSocketAddress) {
        send(byteBuf, messageTranceId, descInetSocketAddress, null);
    }

    public void sendMessageNotResult(String message, InetSocketAddress descInetSocketAddress) {
        send(message.getBytes(StandardCharsets.UTF_8), new Random().nextLong(), descInetSocketAddress, null);
    }


    public void send(byte[] byteBuf, Long messageTranceId, InetSocketAddress descInetSocketAddress, MessageSendListener listener) {
        if (null == descInetSocketAddress) {
            throw new RuntimeException("目标地址还没有设置，" +
                    "请调用 setDescInetSocketAddress(InetSocketAddress descInetSocketAddress)进行设置");
        }
        udpSenderManager.sendUdpMessage(byteBuf, messageTranceId, listener, descInetSocketAddress);
    }

    public void send(String message, Long messageTranceId, InetSocketAddress descInetSocketAddress, MessageSendListener listener) {
        send(message.getBytes(StandardCharsets.UTF_8), messageTranceId, descInetSocketAddress, listener);
    }

    public Boolean sendMessage(byte[] message, Long messageId, MessageSendListener listener, InetSocketAddress inetAddress) {
       return udpSenderManager.sendUdpMessage(message,messageId,listener,inetAddress);
    }

    public Boolean sendUdpMessage(MessageWrapper message) {
        return udpSenderManager.sendUdpMessage(message);
    }


}
