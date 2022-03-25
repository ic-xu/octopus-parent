package io.octopus.udp.receiver.netty;

import io.octopus.udp.config.TransportConfig;
import junit.framework.TestCase;

public class NettyReceiverTest extends TestCase {



    public void testInit() throws InterruptedException {

        NettyReceiver nettyReceiver = new NettyReceiver((messageId, msg) -> true,new TransportConfig());

        nettyReceiver.start();

    }

}