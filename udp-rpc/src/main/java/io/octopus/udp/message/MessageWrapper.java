package io.octopus.udp.message;

import java.net.InetSocketAddress;

public class MessageWrapper {

    private byte[] MessagePackage;

    private Long messageId;

    private MessageSendListener messageSendListener;

    private InetSocketAddress descInetAddress;


    public MessageWrapper(byte[] MessagePackage,Long messageId, MessageSendListener messageSendListener, InetSocketAddress descInetAddress) {
        this.MessagePackage = MessagePackage;
        this.messageSendListener = messageSendListener;
        this.descInetAddress = descInetAddress;
        this.messageId = messageId;

    }

    public MessageSendListener getMessageFuture() {
        return messageSendListener;
    }

    public byte[] getMessagePackage() {
        return MessagePackage;
    }

    public Long getMessageId() {
        return messageId;
    }

    public InetSocketAddress getDescInetAddress() {
        return descInetAddress;
    }

}
