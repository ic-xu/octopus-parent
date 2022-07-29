package io.octopus.router;

import io.handler.codec.mqtt.MqttMessage;
import io.handler.codec.mqtt.MqttPublishMessage;
import io.octopus.kernel.kernel.router.IRouterRegister;
import io.octopus.udp.message.MessageSendListener;
import io.octopus.udp.sender.Sender;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Set;

public class UdpDirectRouter implements RouteMessage2OtherBrokerServer {

    Sender sender;
    private IRouterRegister routerRegister;

    public UdpDirectRouter(IRouterRegister routerRegister) throws IOException {
        this.sender = new Sender();
        this.routerRegister = routerRegister;
    }

    @Override
    public void router(MqttMessage mqttMessage, MessageSendListener messageSendListener) {
        switch (mqttMessage.fixedHeader().messageType()) {
            case PUBLISH:
                MqttPublishMessage message = (MqttPublishMessage) mqttMessage;
                Set<InetSocketAddress> inetSocketAddresses = searchTargetAddress(message.variableHeader().topicName());
                if (null != inetSocketAddresses) {
                    for (InetSocketAddress address : inetSocketAddresses) {
//                        sender.send(MessageEncoderUtils.decodeMessage(mqttMessage), message.getLongId(), address, messageSendListener);
                    }
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
