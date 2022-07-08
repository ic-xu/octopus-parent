package io.octopus.udp.sender;

import io.octopus.udp.config.TransportConfig;
import io.octopus.udp.message.MessageSendListener;
import io.octopus.udp.message.MessageWrapper;
import org.junit.jupiter.api.Disabled;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicInteger;

public class SenderTest {
    @Disabled
    public static void benchmacTest(String[] args) throws IOException {

        TransportConfig transportConfig = new TransportConfig();
        Sender sender = new Sender(transportConfig);
        String re = " ";
        for (int i = 0; i < 200; i++) {
            re = re + i + " ";
        }


//        for (int i = 0; i < 100000; i++) {
//        Long time =System.currentTimeMillis();
//            ff(time ,sender,re);
//        }
        System.out.println("发送结束");
        Scanner scanner = new Scanner(System.in);


        while (true) {
            String s = scanner.nextLine();
            MessageSenderListener messageSenderListener = new MessageSenderListener(System.currentTimeMillis());

        }

    }

    @Disabled
    static class MessageSenderListener implements MessageSendListener {
        private Long time;
        private AtomicInteger a = new AtomicInteger(0);
        Logger logger = LoggerFactory.getLogger(MessageSenderListener.class);

        public MessageSenderListener(Long time) {
            this.time = time;
        }

        @Override
        public void onSuccess(MessageWrapper messageWrapper) {
            logger.info("{} 发送成功,耗时 {} 毫秒", messageWrapper.getMessageId(), System.currentTimeMillis() - time);
        }

        @Override
        public void onError(Exception messageWrapper) {
            logger.error("{} 消息发送失败 ......", messageWrapper.getMessage());
        }
    }
}