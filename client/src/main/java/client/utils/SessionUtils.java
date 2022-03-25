package client.utils;

import client.protocol.MqttConnectOptions;

public class SessionUtils {



    public static String getSessionKey(MqttConnectOptions options){
        return options.getHost()+"-"+options.getPort();

    }
}
