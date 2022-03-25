package io.octopus.udp.message;

import io.netty.buffer.ByteBuf;

public interface MessageReceiverListener {

    /**
     * 这个方法不能阻塞，应为只有这个方法调用成功之后，才会响应给对方消息收到了。
     * 如果阻塞，可能导致对方认为消息丢失以至于多次回掉这个消息。
     *
     * @param messageId 消息Id
     * @param msg 收到的消息
     * @return boolean
     */
    Boolean onMessage(Long messageId, ByteBuf msg);

}
