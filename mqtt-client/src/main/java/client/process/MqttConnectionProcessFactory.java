package client.process;

import client.MqttCallback;
import client.Session;
import client.SessionRegister;
import client.protocol.MqttConnectOptions;
import client.utils.SessionUtils;
import io.netty.channel.Channel;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MqttConnectionProcessFactory {

    private MqttCallback callback;

    private SessionRegister sessionRegister;

    private MqttConnectionProcessFactory(){

    }

    public MqttConnectionProcessFactory(MqttCallback callback, SessionRegister sessionRegister) {
        this.callback = callback;
        this.sessionRegister = sessionRegister;
    }





    public MQTTConnectionProcess createMqttConnectionProcess(Channel channel) {
        return new MQTTConnectionProcess(sessionRegister, channel,callback);
    }



}
