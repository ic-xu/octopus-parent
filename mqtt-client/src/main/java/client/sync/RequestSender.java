package client.sync;

import io.handler.codec.mqtt.*;
import io.netty.channel.Channel;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static io.handler.codec.mqtt.MqttQoS.AT_LEAST_ONCE;

public class RequestSender {


    public static MqttSubAckMessage sendMessageMqttSubscribeMessage(MqttSubscribeMessage subscribeMessage, long timeout, Channel remoteChannel) throws ExecutionException, InterruptedException {
        SyncRequest<MqttSubscribeMessage, MqttSubAckMessage> mqttSubAckMessageSyncRequest = new SyncRequest<>(subscribeMessage,
            (short) subscribeMessage.variableHeader().messageId(), timeout, remoteChannel);
        Future<MqttSubAckMessage> submit = SyncRequestManager.INSTANCE.getSender().submit(mqttSubAckMessageSyncRequest);
        return submit.get();
    }


    public static MqttUnsubAckMessage sendMessageMqttUnsubscribeMessage(MqttUnsubscribeMessage unsubscribeMessage, long timeout, Channel remoteChannel) throws ExecutionException, InterruptedException {
        SyncRequest<MqttUnsubscribeMessage, MqttUnsubAckMessage> mqttSubAckMessageSyncRequest = new SyncRequest<>(unsubscribeMessage,
            (short) unsubscribeMessage.variableHeader().messageId(), timeout, remoteChannel);
        Future<MqttUnsubAckMessage> submit = SyncRequestManager.INSTANCE.getSender().submit(mqttSubAckMessageSyncRequest);
        return submit.get();
    }


    public static MqttPubRelMessage sendMessageMqttPubRelMessage(MqttPubRelMessage mqttPubRelMessage, long timeout, Channel remoteChannel) throws ExecutionException, InterruptedException {
        SyncRequest<MqttPubRelMessage, MqttPubRelMessage> mqttPubRelMessageRequest = new SyncRequest<>(mqttPubRelMessage,
            (short) mqttPubRelMessage.variableHeader().messageId(), timeout, remoteChannel);
        Future<MqttPubRelMessage> submit = SyncRequestManager.INSTANCE.getSender().submit(mqttPubRelMessageRequest);
        return submit.get();
    }


    public static Boolean sendMqttPublishMessage(MqttPublishMessage publishMessage, long timeout, Channel remoteChannel) throws ExecutionException, InterruptedException, TimeoutException {
        MqttQoS mqttQoS = publishMessage.fixedHeader().qosLevel();
        switch (mqttQoS) {
            case AT_MOST_ONCE:
                remoteChannel.writeAndFlush(publishMessage);
                break;
            case AT_LEAST_ONCE:
                SyncRequest<MqttPublishMessage, MqttPubAckMessage> MqttPublishMessageQos1 = new SyncRequest<>(publishMessage,
                    (short) publishMessage.variableHeader().packetId(), timeout, remoteChannel);
                Future<MqttPubAckMessage> submit = SyncRequestManager.INSTANCE.getSender().submit(MqttPublishMessageQos1);
                if (submit.get(timeout, TimeUnit.MILLISECONDS) != null) {
                    return true;
                }
                return false;
            case EXACTLY_ONCE:
                SyncRequest<MqttPublishMessage, MqttPubRecMessage> MqttPublishMessageQos2 = new SyncRequest<>(publishMessage,
                    (short) publishMessage.variableHeader().packetId(), timeout, remoteChannel);
                Future<MqttPubRecMessage> publicMessageRecRespose = SyncRequestManager.INSTANCE.getSender().submit(MqttPublishMessageQos2);
                MqttPubRecMessage mqttPubRecMessage = publicMessageRecRespose.get(timeout, TimeUnit.MILLISECONDS);
                if (null != mqttPubRecMessage) {
                    MqttPubRelMessage mqttPubRelMessage = new MqttPubRelMessage(new MqttFixedHeader(MqttMessageType.PUBREL, false, AT_LEAST_ONCE, false, 2),
                        MqttMessageIdVariableHeader.from(mqttPubRecMessage.variableHeader().messageId()));
                    SyncRequest<MqttPubRelMessage, MqttPubCompMessage> mqttPubRecMessageSyncRequest = new SyncRequest<>(mqttPubRelMessage,
                        (short) publishMessage.variableHeader().packetId(), timeout, remoteChannel);
                    Future<MqttPubCompMessage> submit1 = SyncRequestManager.INSTANCE.getSender().submit(mqttPubRecMessageSyncRequest);
                    return null != submit1.get();
                } else {
                    return false;
                }
            default:
                throw new RuntimeException("qos is error  " + mqttQoS.value());
        }

        return false;
    }


    /**
     * 发送自定义消息，这里可能需要用到回执。
     * @param mqttCustomerMessage
     * @param timeout
     * @param remoteChannel
     * @return
     * @throws ExecutionException
     * @throws InterruptedException
     */
    public static MqttSubAckMessage sendCustomerMessage(MqttCustomerMessage mqttCustomerMessage, long timeout, Channel remoteChannel) throws ExecutionException, InterruptedException {
//        SyncRequest<MqttSubscribeMessage, MqttSubAckMessage> mqttSubAckMessageSyncRequest = new SyncRequest<>(subscribeMessage,
//            (short) subscribeMessage.variableHeader().messageId(), timeout, remoteChannel);
//        Future<MqttSubAckMessage> submit = SyncRequestManager.INSTANCE.getSender().submit(mqttSubAckMessageSyncRequest);
//        return submit.get();
        return null;
    }

}
