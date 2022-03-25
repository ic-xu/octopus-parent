package client.handler;

import client.process.MQTTConnectionProcess;
import client.process.MqttConnectionProcessFactory;
import client.protocol.ClientProtocolUtil;
import client.utils.NettyChannelUtils;
import io.handler.codec.mqtt.*;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;


public class MqttClientHandler extends SimpleChannelInboundHandler<Object> {


    private MqttConnectionProcessFactory mqttConnectionProcessFactory;

    public MqttClientHandler(MqttConnectionProcessFactory mqttConnectionProcessFactory) {
        this.mqttConnectionProcessFactory = mqttConnectionProcessFactory;
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        ctx.writeAndFlush(ClientProtocolUtil.getPingMessage());
        super.userEventTriggered(ctx, evt);
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        // 每次连接上来 使用连接工厂创建一个连接管理器
        MQTTConnectionProcess mqttConnectionProcess = mqttConnectionProcessFactory.createMqttConnectionProcess(ctx.channel());

        //把相应的连接对象封装在channel中，以供后续使用
        NettyChannelUtils.mqttConnection(ctx.channel(), mqttConnectionProcess);
    }


    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Object msg) {
        NettyChannelUtils.mqttConnection(ctx.channel()).handleMessage((MqttMessage) msg);

    }
}
