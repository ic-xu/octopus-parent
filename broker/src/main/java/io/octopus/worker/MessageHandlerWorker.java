package io.octopus.worker;

import io.handler.codec.mqtt.MqttPublishMessage;
import io.octopus.scala.broker.PostOffice;

import java.util.concurrent.LinkedBlockingQueue;

/**
 * @author chenxu
 * @version 1
 * @date $ $
 */
public class MessageHandlerWorker extends Thread{
    private final LinkedBlockingQueue<MqttPublishMessage> messageQueue = new LinkedBlockingQueue<>();
    private final PostOffice postOffice;

    public MessageHandlerWorker(PostOffice postOffice) {
        this.postOffice = postOffice;
    }

    public void processMessage(MqttPublishMessage msg) {
        try {
            messageQueue.put(msg);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void run() {
        for ( ; ; ) {
            try {
                MqttPublishMessage take = messageQueue.take();
                postOffice.internalPublish(take);
                take.payload().release();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

}
