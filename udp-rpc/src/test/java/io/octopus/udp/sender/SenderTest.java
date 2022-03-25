package io.octopus.udp.sender;

import io.handler.codec.mqtt.*;
import io.handler.codec.mqtt.utils.MqttEncoderUtils;
import io.netty.buffer.Unpooled;
import io.octopus.udp.config.TransportConfig;
import io.octopus.udp.message.MessageSendListener;
import io.octopus.udp.message.MessageWrapper;
import junit.framework.TestCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Random;
import java.util.Scanner;

public class SenderTest extends TestCase {



    public static void main(String[] args) throws IOException {

        TransportConfig transportConfig = new TransportConfig();
        Sender sender = new Sender(transportConfig);
        String re = " ";
        for (int i = 0; i < 2000; i++) {
            re = re + i + " ";
        }

        MqttFixedHeader mqttFixedHeader = new MqttFixedHeader(MqttMessageType.PUBLISH, false, MqttQoS.AT_LEAST_ONCE, false, 0);
        MqttPublishVariableHeader mqttPublishVariableHeader = new MqttPublishVariableHeader("test", new Random().nextInt(65535));
        MqttPublishMessage mqttPublishMessage;

//        for (int i = 0; i < 100000; i++) {
//        Long time =System.currentTimeMillis();
//            ff(time ,sender,re);
//        }
        System.out.println("发送结束");
        Scanner scanner = new Scanner(System.in);



        while (true) {
            String s = scanner.nextLine();
            Long time = System.currentTimeMillis();
            MessageSenderListener messageSenderListener = new MessageSenderListener(time);
            mqttPublishMessage = new MqttPublishMessage(mqttFixedHeader, mqttPublishVariableHeader,
                    Unpooled.wrappedBuffer((s).getBytes(StandardCharsets.UTF_8)));

//            for (int i = 0; i < 100000; i++) {
//            ff(sender, mqttPublishMessage,messageSenderListener);

                sender.sendUdpMessage(new MessageWrapper(MqttEncoderUtils.decodeMessage(mqttPublishMessage),
                        mqttPublishMessage.getMqttMessageTranceId(),
                        messageSenderListener,
                        new InetSocketAddress("127.0.0.1",
                                25221)));
//            }
        }

    }


    private static void ff(Sender sender, MqttMessage mqttMessage, MessageSenderListener messageSenderListener) {

//        sender.send(re.trim(), new InetSocketAddress("172.30.241.5",2522),new MessageSendListener(){
        sender.send(MqttEncoderUtils.decodeMessage(mqttMessage), new Random().nextLong(), new InetSocketAddress("172.20.73.88", 2522),
                messageSenderListener);
    }

    static class MessageSenderListener implements MessageSendListener {
        private Long time;
        Logger logger = LoggerFactory.getLogger(MessageSenderListener.class);
        public MessageSenderListener(Long time) {
            this.time = time;
        }

        @Override
        public void onSuccess(MessageWrapper messageWrapper) {
            logger.info("{} 发送成功,耗时 {} 毫秒",messageWrapper.getMessageId(),System.currentTimeMillis() - time);
        }

        @Override
        public void onError(Exception messageWrapper) {
            logger.error("{} 消息发送失败 ......",messageWrapper.getMessage());
        }
    }
}