package io.octopus.udp.receiver.netty;

import io.octopus.udp.config.TransportConfig;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

public class NettyReceiverTest {


    @Disabled
    @Test
    public void testInit() throws InterruptedException {

        NettyReceiver nettyReceiver = new NettyReceiver((messageId, msg) -> true, new TransportConfig());

//        nettyReceiver.start();

    }

}