package io.octopus.broker.router;

import io.handler.codec.mqtt.MqttMessage;
import io.handler.codec.mqtt.MqttPublishMessage;
import io.handler.codec.mqtt.utils.MqttEncoderUtils;
import io.octopus.persistence.RouterRegister;
import io.octopus.udp.message.MessageSendListener;
import io.octopus.udp.sender.Sender;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Set;

public class UdpDirectRouter implements RouteMessage2OtherBrokerServer{

    Sender sender;
    private RouterRegister routerRegister;

    public UdpDirectRouter(RouterRegister routerRegister) throws IOException {
        this.sender = new Sender();
        this.routerRegister = routerRegister;
    }

    @Override
    public void router(MqttMessage mqttMessage,MessageSendListener messageSendListener)  {
        switch (mqttMessage.fixedHeader().messageType()){
            case PUBLISH:
                MqttPublishMessage message = (MqttPublishMessage) mqttMessage;
                Set<InetSocketAddress> inetSocketAddresses = searchTargetAddress(message.variableHeader().topicName());
                if(null!=inetSocketAddresses){
                    inetSocketAddresses.forEach(address ->
                            sender.send(MqttEncoderUtils.decodeMessage(mqttMessage),message.getMqttMessageTranceId(),address,messageSendListener));
                }
                break;
            case CUSTOMER:
                break;
            default:
                break;
        }
    }

    private Set<InetSocketAddress> searchTargetAddress(String topicName) {
        return routerRegister.getAddressByTopic(topicName);
    }
}
