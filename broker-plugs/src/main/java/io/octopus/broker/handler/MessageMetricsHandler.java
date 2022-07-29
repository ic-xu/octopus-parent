package io.octopus.broker.handler;

import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.util.Attribute;
import io.netty.util.AttributeKey;
import io.octopus.kernel.kernel.metrics.MessageMetrics;
import io.octopus.kernel.kernel.metrics.MessageMetricsCollector;

import static io.netty.channel.ChannelFutureListener.CLOSE_ON_FAILURE;

public class MessageMetricsHandler extends ChannelDuplexHandler {

    private static final AttributeKey<MessageMetrics> ATTR_KEY_METRICS = AttributeKey.valueOf("MessageMetrics");

    private MessageMetricsCollector messageMetricsCollector;

    public MessageMetricsHandler(MessageMetricsCollector collector) {
        messageMetricsCollector = collector;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        Attribute<MessageMetrics> attr = ctx.channel().attr(ATTR_KEY_METRICS);
        attr.set(new MessageMetrics());

        super.channelActive(ctx);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        MessageMetrics metrics = ctx.channel().attr(ATTR_KEY_METRICS).get();
        metrics.incrementRead(1);
        ctx.fireChannelRead(msg);
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        MessageMetrics metrics = ctx.channel().attr(ATTR_KEY_METRICS).get();
        metrics.incrementWrote(1);
        ctx.write(msg, promise).addListener(CLOSE_ON_FAILURE);
    }

    @Override
    public void close(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
        MessageMetrics metrics = ctx.channel().attr(ATTR_KEY_METRICS).get();
        messageMetricsCollector.sumReadMessages(metrics.messagesRead());
        messageMetricsCollector.sumWroteMessages(metrics.messagesWrote());
        super.close(ctx, promise);
    }

    public static MessageMetrics getMessageMetrics(Channel channel) {
        return channel.attr(ATTR_KEY_METRICS).get();
    }
}
