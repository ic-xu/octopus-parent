package client.utils;

import client.process.MQTTConnectionProcess;
import client.protocol.MqttConnectOptions;
import io.netty.channel.Channel;
import io.netty.util.AttributeKey;

public class NettyChannelUtils {


    private static final String ATTR_KEY_CONNECT_OPTION="connect_option";
    private static final AttributeKey<Object> ATTR_KEY_CONNECTION_OP_TION = AttributeKey.valueOf(ATTR_KEY_CONNECT_OPTION);

    public static void setConnectOption(Channel channel, MqttConnectOptions options) {
        channel.attr(ATTR_KEY_CONNECTION_OP_TION).set(options);
    }

    public static MqttConnectOptions getConnectOption(Channel channel){
        return (MqttConnectOptions) channel.attr(ATTR_KEY_CONNECTION_OP_TION).get();
    }




    private static final String ATTR_CONNECTION = "connection";
    private static final AttributeKey<Object> ATTR_KEY_CONNECTION = AttributeKey.valueOf(ATTR_CONNECTION);
    public static void mqttConnection(Channel channel, MQTTConnectionProcess mqttConnectionProcess) {
        channel.attr(ATTR_KEY_CONNECTION).set(mqttConnectionProcess);
    }

    public static MQTTConnectionProcess mqttConnection(Channel channel) {
        return (MQTTConnectionProcess) channel.attr(ATTR_KEY_CONNECTION).get();
    }

}
