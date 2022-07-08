package io.webrtc.signaler;

import io.handler.codec.mqtt.utils.MqttMessageDecoderUtils;
import io.netty.buffer.ByteBuf;
import io.octopus.udp.config.TransportConfig;
import io.octopus.udp.message.MessageReceiverListener;
import io.octopus.udp.receiver.netty.NettyReceiver;
import io.webrtc.signaler.handler.MessageHandler;

public class SignalerServer implements MessageReceiverListener {

   private final MessageHandler messageHandler = new MessageHandler();

    public static void main(String[] args) throws InterruptedException {
        SignalerServer server = new SignalerServer();
        TransportConfig transportConfig = new TransportConfig();
        transportConfig.put("udp.transport.port","25221");
        NettyReceiver nettyReceiver = new NettyReceiver(server,transportConfig);
        nettyReceiver.start();

    }

    @Override
    public Boolean onMessage(Long messageId, ByteBuf msg) {
       return messageHandler.receiverMessage(MqttMessageDecoderUtils.decode(messageId,msg.array()));
    }
}
