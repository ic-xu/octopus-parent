package io.octopus.worker;

import io.handler.codec.mqtt.MqttPublishMessage;
import io.octopus.kernel.kernel.postoffice.IPostOffice;

import java.util.concurrent.LinkedBlockingQueue;

/**
 * @author chenxu
 * @version 1
 * @date $ $
 */
public class MessageHandlerWorker extends Thread{
    private final LinkedBlockingQueue<MqttPublishMessage> messageQueue = new LinkedBlockingQueue<>();
    private final IPostOffice postOffice;

    public MessageHandlerWorker(IPostOffice postOffice) {
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
//                TODO postOffice.internalPublish(take);
                take.payload().release();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

}
