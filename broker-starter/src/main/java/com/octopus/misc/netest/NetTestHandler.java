package com.octopus.misc.netest;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 
 * @author yama
 * 9 May, 2015
 */
public class NetTestHandler extends SimpleChannelInboundHandler {
	private static Logger logger= LoggerFactory.getLogger(NetTestHandler.class);
	NetTestClient netTestClient;
	public NetTestHandler(NetTestClient client) {
		this.netTestClient=client;
	}
	//
    @Override
    public void channelActive(ChannelHandlerContext ctx) {
    	try {
			netTestClient.onConnect();
		} catch (Exception e) {
			logger.error("{}",e);
		}
    }
    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
    	try {
			netTestClient.onDisConnect();
		} catch (Exception e) {
			logger.error("{}",e);
		}
    }

	@Override
	protected void channelRead0(ChannelHandlerContext channelHandlerContext, Object o) throws Exception {
		try {
			ByteBuf buffer=(ByteBuf) o;
			netTestClient.onMessage(Unpooled.copiedBuffer(buffer));
		} catch (Exception e) {
			logger.error("{}",e);
		}
	}

	//
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
    	logger.error("{}",cause);
    	ctx.close();
    }
}