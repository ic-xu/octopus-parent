package io.game.logic;

import io.netty.buffer.ByteBuf;
import io.octopus.udp.config.TransportConfig;
import io.octopus.udp.message.MessageReceiverListener;
import io.octopus.udp.receiver.netty.NettyReceiver;

public class Server implements MessageReceiverListener {

    public static void main(String[] args) throws InterruptedException {
        Server server = new Server();
        TransportConfig transportConfig = new TransportConfig();
        transportConfig.put("udp.transport.port","25221");
        NettyReceiver nettyReceiver = new NettyReceiver(server,transportConfig);
        nettyReceiver.start();

    }

    @Override
    public Boolean onMessage(Long messageId, ByteBuf msg) {
        System.out.println(new String(msg.array()));
        return true;
    }
}
