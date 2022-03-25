package io.octopus.udp.message;

import java.net.InetSocketAddress;

public class MessageWrapper {

    private byte[] messagePackage;

    private Long messageId;

    private MessageSendListener messageSendListener;

    private InetSocketAddress descInetAddress;


    public MessageWrapper(byte[] messagePackage,Long messageId, MessageSendListener messageSendListener, InetSocketAddress descInetAddress) {
        this.messagePackage = messagePackage;
        this.messageSendListener = messageSendListener;
        this.descInetAddress = descInetAddress;
        this.messageId = messageId;

    }

    public MessageSendListener getMessageFuture() {
        return messageSendListener;
    }

    public byte[] getMessagePackage() {
        return messagePackage;
    }

    public Long getMessageId() {
        return messageId;
    }

    public InetSocketAddress getDescInetAddress() {
        return descInetAddress;
    }

}
