package client;

import client.process.MqttConnectionProcessFactory;
import client.protocol.MqttConnectOptions;
import io.handler.codec.mqtt.MqttCustomerMessage;
import io.handler.codec.mqtt.MqttMessage;
import io.handler.codec.mqtt.MqttVersion;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

public class MqttClient {


    public static void main(String[] args) throws InterruptedException, ExecutionException, TimeoutException {
        MqttConnectOptions mqttConnectOptions = new MqttConnectOptions();
        mqttConnectOptions.setHost("localhost");
        mqttConnectOptions.setPort(1883);
        mqttConnectOptions.setClientIdentifier("test-000000" + 1);
        mqttConnectOptions.setUserName("admin");
        mqttConnectOptions.setPassword("passwd".getBytes());
        mqttConnectOptions.setHasWillFlag(false);
        mqttConnectOptions.setHasCleanSession(false);
        mqttConnectOptions.setHasUserName(true);
        mqttConnectOptions.setHasPassword(true);
        mqttConnectOptions.setKeepAliveTime(10);
        mqttConnectOptions.setMqttVersion(MqttVersion.MQTT_3_1);
        SessionRegister sessionRegister = new SessionRegister();
        MqttConnectionProcessFactory mqttConnectionProcessFactory = new MqttConnectionProcessFactory(new MqttCallback() {
            @Override
            public void customerMessageArrived(MqttCustomerMessage message) {
                byte[] bytes = new byte[message.payload().readableBytes()];
                message.payload().readBytes(bytes);
                String s = new String(bytes, StandardCharsets.UTF_8);
                System.out.println(s);
            }

            @Override
            public void connectError(String errorMsg) {

            }

            @Override
            public void connectComplete(boolean reconnect, InetSocketAddress remoteAddress) {

            }

            @Override
            public void connectionLost(Throwable cause) {

            }

            @Override
            public void messageArrived(String topic, MqttMessage message) throws Exception {

            }

            @Override
            public void deliveryComplete(Object token) {

            }
        },sessionRegister);
        sessionRegister.setClient(NettyClientAccepter.getInstance(mqttConnectionProcessFactory));

        sessionRegister.add(mqttConnectOptions);



//        NettyClientAccepter.getInstance(mqttConnectionProcessFactory).doConnectUDP();
//        NettyClientAccepter.getInstance(mqttConnectionProcessFactory).doConnect(session);

//         List<MqttTopicSubscription> subscriptions=new ArrayList<>();
//        subscriptions.add(new MqttTopicSubscription("test", MqttQoS.AT_LEAST_ONCE));
//
//        MqttFixedHeader mqttFixedHeader =
//            new MqttFixedHeader(MqttMessageType.SUBSCRIBE, false, MqttQoS.AT_LEAST_ONCE, false, 0);
//        MqttMessageIdAndPropertiesVariableHeader mqttVariableHeader =
//            new MqttMessageIdAndPropertiesVariableHeader(10, null);
//        MqttSubscribePayload mqttSubscribePayload = new MqttSubscribePayload(subscriptions);
//        MqttSubscribeMessage subscribeMessage = new MqttSubscribeMessage(mqttFixedHeader, mqttVariableHeader, mqttSubscribePayload);
//
//
//        MqttSubAckMessage mqttSubAckMessage = RequestSender.sendMessageMqttSubscribeMessage(subscribeMessage,6000, session.getChannel());
//
//        System.out.println(mqttSubAckMessage);




//        ByteBuf byteBuf = Unpooled.wrappedBuffer("123456".getBytes());
//        MqttPublishVariableHeader test = new MqttPublishVariableHeader("test", 5);
//        MqttPublishMessage message = new MqttPublishMessage(new MqttFixedHeader(MqttMessageType.PUBLISH,
//            false,MqttQoS.EXACTLY_ONCE,false,byteBuf.readableBytes()+2+"test".getBytes().length+2),test
//            ,byteBuf);
//        Boolean aBoolean = RequestSender.sendMqttPublishMessage(message, 60000, session.getChannel());
//        System.out.println(aBoolean);


//        MqttFixedHeader mqttFixedHeader1 =
//            new MqttFixedHeader(MqttMessageType.SUBSCRIBE, false, MqttQoS.AT_LEAST_ONCE, false, 0);
//        MqttMessageIdAndPropertiesVariableHeader mqttVariableHeader1 =
//            new MqttMessageIdAndPropertiesVariableHeader(10, null);
//        MqttSubscribePayload mqttSubscribePayload1 = new MqttSubscribePayload(subscriptions);
//        MqttSubscribeMessage subscribeMessage1 = new MqttSubscribeMessage(mqttFixedHeader1, mqttVariableHeader1, mqttSubscribePayload1);
//
//
//        MqttSubAckMessage mqttSubAckMessage1 = RequestSender.sendMessageMqttSubscribeMessage(subscribeMessage1,6000, session.getChannel());
//
//        System.out.println(mqttSubAckMessage1);

    }





}
