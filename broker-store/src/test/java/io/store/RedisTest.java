package io.store;

import io.handler.codec.mqtt.*;
import io.netty.buffer.ByteBuf;
import org.junit.Before;
import org.junit.Test;
import org.redisson.Redisson;
import org.redisson.api.RMap;
import org.redisson.api.RedissonClient;
import org.redisson.codec.JsonJacksonCodec;
import org.redisson.config.Config;
import org.redisson.connection.ConnectionListener;

import java.net.InetSocketAddress;

public class RedisTest {

    private RedissonClient client;
    RMap<Integer, String> testQueue;


    @Before
    public void before(){
        Config redisSionConfig  = new Config();
        redisSionConfig.useSingleServer().setAddress("redis://172.20.73.93:6379");
        redisSionConfig.setCodec(new JsonJacksonCodec());
        redisSionConfig.setConnectionListener(new ConnectionListener() {
            @Override
            public void onConnect(InetSocketAddress inetSocketAddress) {
                System.out.println("redis onConnect ....");
            }

            @Override
            public void onDisconnect(InetSocketAddress inetSocketAddress) {
                System.out.println("redis onDisconnect ....");
                System.exit(1);
            }
        });
        client = Redisson.create(redisSionConfig);
        testQueue = client.getMap("testQueue");
    }


    @Test
    public void offer(){
        long startTime = System.currentTimeMillis();
//        for (int i = 2; i < 1000000; i++) {
//            MqttPublishMessage mqttPublishMessage = publishNotRetainedDuplicated(i % 65535 + 1, "test/bb/cc" + i, MqttQoS.AT_LEAST_ONCE,
//                    Unpooled.wrappedBuffer(("hhhh-ggg-ddd" + i).getBytes(StandardCharsets.UTF_8)));
////            testQueue.offer(mqttPublishMessage);
//            testQueue.put(i, MqttEncoderUtils.decodeMessage(mqttPublishMessage));
//        }


        for (int i = 2; i < 100000; i++) {
            String bytes = testQueue.get(i);
//            MqttPublishMessage poll = (MqttPublishMessage) MqttDecoderUtils.decode(bytes);
            if (null != bytes) {
//                System.out.println(bytes);
            }

        }
        System.err.println(System.currentTimeMillis() - startTime);

    }

    private MqttPublishMessage publishNotRetainedDuplicated(int packetId, String topic, MqttQoS qos, ByteBuf payload) {
        MqttFixedHeader fixedHeader = new MqttFixedHeader(MqttMessageType.PUBLISH, true, qos, false, 0);
        MqttPublishVariableHeader varHeader = new MqttPublishVariableHeader(topic.toString(), packetId);
        return new MqttPublishMessage(fixedHeader, varHeader, payload);
    }

}
