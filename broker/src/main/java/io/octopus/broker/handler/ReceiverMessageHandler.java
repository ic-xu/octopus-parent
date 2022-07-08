//package io.octopus.broker.handler;
//
//import io.handler.codec.mqtt.MqttMessage;
//import io.handler.codec.mqtt.MqttPublishMessage;
//import io.handler.codec.mqtt.utils.MessageDecoderUtils;
//import io.octopus.scala.mqtt.broker.PostOffice;
//import io.octopus.udp.message.MessageReceiverListener;
//import io.netty.buffer.ByteBuf;
//
//import java.util.concurrent.LinkedBlockingQueue;
//
//public class ReceiverMessageHandler implements MessageReceiverListener {
//
//    private PostOffice msgDispatcher;
//
//    private MessageHandlerWorker[] worker = new MessageHandlerWorker[1];
//
//
//    public ReceiverMessageHandler(PostOffice msgDispatcher) {
//        this.msgDispatcher = msgDispatcher;
//        for (int i = 0; i < worker.length; i++) {
//            worker[i] = new MessageHandlerWorker();
//            worker[i].setDaemon(true);
//            worker[i].setName("udp receiver processor " + i);
//            worker[i].start();
//        }
//    }
//
//
//    @Override
//    public Boolean onMessage(Long messageId, ByteBuf msg) {
//        MqttMessage decode = MessageDecoderUtils.decode(messageId,msg.array());
//        if (decode instanceof MqttPublishMessage) {
//            MqttPublishMessage publishMessage = (MqttPublishMessage) decode;
//            int i = publishMessage.variableHeader().topicName().hashCode();
//            int index = (worker.length - 1) % i;
//            worker[index].processMessage(publishMessage);
//        }
//        return true;
//    }
//
//
//    class MessageHandlerWorker extends Thread  {
//        private final LinkedBlockingQueue<MqttPublishMessage> messageQueue = new LinkedBlockingQueue<>();
//
//        public void processMessage(MqttPublishMessage msg) {
//            try {
//                messageQueue.put(msg);
//            } catch (InterruptedException e) {
//                e.printStackTrace();
//            }
//        }
//
//        @Override
//        public void run() {
//            for ( ; ; ) {
//                try {
//                    MqttPublishMessage take = messageQueue.take();
//                    msgDispatcher.internalPublish(take);
//                    take.payload().release();
//                } catch (InterruptedException e) {
//                    e.printStackTrace();
//                }
//            }
//        }
//    }
//
//}
