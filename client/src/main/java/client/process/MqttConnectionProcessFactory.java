package client.process;

import client.MqttCallback;
import client.SessionRegister;
import io.netty.channel.Channel;

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
