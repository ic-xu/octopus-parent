package io.octopus.broker.handler;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageEncoder;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;

import java.util.List;

public class WebSocketEncoder extends MessageToMessageEncoder<ByteBuf> {

    @Override
    protected void encode(ChannelHandlerContext chc, ByteBuf bb, List<Object> out) {
        // convert the ByteBuf to a WebSocketFrame
        BinaryWebSocketFrame result = new BinaryWebSocketFrame();
        result.content().writeBytes(bb);
        out.add(result);
    }
}