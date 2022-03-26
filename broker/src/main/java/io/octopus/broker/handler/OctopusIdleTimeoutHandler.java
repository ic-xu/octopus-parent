package io.octopus.broker.handler;

import io.octopus.base.utils.NettyUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;

import static io.netty.channel.ChannelFutureListener.CLOSE_ON_FAILURE;

/**
 * timeoutHandler
 * @author user
 */
@Sharable
public class OctopusIdleTimeoutHandler extends ChannelDuplexHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(OctopusIdleTimeoutHandler.class);

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent) {
            IdleState e = ((IdleStateEvent) evt).state();
            if (e == IdleState.READER_IDLE) {
                LOGGER.trace("Firing channel inactive event. MqttClientId = {}.", NettyUtils.clientID(ctx.channel()));
                // fire a close that then fire channelInactive to trigger publish of Will
                ctx.close().addListener(CLOSE_ON_FAILURE);
            }
        } else {
            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("Firing Netty event CId = {}, eventClass = {}", NettyUtils.clientID(ctx.channel()),
                          evt.getClass().getName());
            }
            super.userEventTriggered(ctx, evt);
        }
    }
}
