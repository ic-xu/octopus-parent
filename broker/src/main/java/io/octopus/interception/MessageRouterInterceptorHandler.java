package io.octopus.interception;

import io.handler.codec.mqtt.MqttPublishMessage;
import io.octopus.broker.Server;
import io.octopus.interception.messages.InterceptConnectMessage;
import io.octopus.interception.messages.InterceptPublishMessage;
import io.octopus.interception.messages.InterceptSubscribeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MessageRouterInterceptorHandler extends AbstractInterceptHandler{

    Logger logger = LoggerFactory.getLogger(this.getClass().getName());

    private Server server;


    public MessageRouterInterceptorHandler(Server server){
//        this.config = config;
        this.server = server;
    }

    @Override
    public String getID() {
        return "messageRouterInterceptorHandler";
    }

    @Override
    public void onConnect(InterceptConnectMessage msg) {
        super.onConnect(msg);
//        System.out.println(msg);
    }



    @Override
    public void onSubscribe(InterceptSubscribeMessage msg) {
        super.onSubscribe(msg);
    }

    @Override
    public void onBeforePublish(MqttPublishMessage msg) {

//        server.getRouteMessage2OtherBrokerServer().router(msg, new MessageSendListener() {
//            @Override
//            public void onSuccess(MessageWrapper messageWrapper) {
////                System.out.println(messageWrapper);
//            }
//
//            @Override
//            public void onError(Exception e) {
//                logger.error(e.getMessage());
//            }
//        });
    }

    @Override
    public void onPublish(InterceptPublishMessage msg) {

    }


}
