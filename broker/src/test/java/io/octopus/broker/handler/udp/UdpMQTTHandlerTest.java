package io.octopus.broker.handler.udp;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import junit.framework.TestCase;

import java.nio.charset.StandardCharsets;

public class UdpMQTTHandlerTest extends TestCase {

    public void testByteBuf() {
        byte[] bytes = "FFFF".getBytes(StandardCharsets.UTF_8);

        ByteBuf byteBuf = Unpooled.buffer(bytes.length+1);
        byteBuf.writeByte(2);
        byteBuf.writeBytes(bytes);


        byte b = byteBuf.readByte();
        System.out.println(b);
        System.out.println(byteBuf.toString(StandardCharsets.UTF_8));

    }
}