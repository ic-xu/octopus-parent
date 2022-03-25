package io.webrtc.signaler.handler;

import io.handler.codec.mqtt.MqttMessage;

import java.util.Queue;
import java.util.concurrent.LinkedBlockingDeque;

public class MessageHandler {

    private final Queue<MqttMessage> messageQueue = new LinkedBlockingDeque<>();

    public Boolean receiverMessage(MqttMessage message){
       return messageQueue.add(message);
    }


}
