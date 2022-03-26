
package io.octopus.broker.handler;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.util.Attribute;
import io.netty.util.AttributeKey;
import io.octopus.broker.metrics.BytesMetrics;
import io.octopus.broker.metrics.BytesMetricsCollector;

import static io.netty.channel.ChannelFutureListener.CLOSE_ON_FAILURE;

/**
 * @author user
 */
public class BytesMetricsHandler extends ChannelDuplexHandler {

    private static final AttributeKey<BytesMetrics> ATTR_KEY_METRICS = AttributeKey.valueOf("BytesMetrics");

    private BytesMetricsCollector bytesMetricsCollector;

    public BytesMetricsHandler(BytesMetricsCollector collector) {
        bytesMetricsCollector = collector;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        Attribute<BytesMetrics> attr = ctx.channel().attr(ATTR_KEY_METRICS);
        attr.set(new BytesMetrics());

        super.channelActive(ctx);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        BytesMetrics metrics = ctx.channel().attr(ATTR_KEY_METRICS).get();
        metrics.incrementRead(((ByteBuf) msg).readableBytes());
        ctx.fireChannelRead(msg);
//        if(((ByteBuf) msg).refCnt()>1){
//            ((ByteBuf) msg).release();
//        }
//        System.out.println(msg);
//        ReferenceCountUtil.release(msg);
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        BytesMetrics metrics = ctx.channel().attr(ATTR_KEY_METRICS).get();
        metrics.incrementWrote(((ByteBuf) msg).writableBytes());
        ctx.write(msg, promise).addListener(CLOSE_ON_FAILURE);
    }

    @Override
    public void close(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
        BytesMetrics metrics = ctx.channel().attr(ATTR_KEY_METRICS).get();
        bytesMetricsCollector.sumReadBytes(metrics.readBytes());
        bytesMetricsCollector.sumWroteBytes(metrics.wroteBytes());
        super.close(ctx, promise);
    }

    public static BytesMetrics getBytesMetrics(Channel channel) {
        return channel.attr(ATTR_KEY_METRICS).get();
    }
}
