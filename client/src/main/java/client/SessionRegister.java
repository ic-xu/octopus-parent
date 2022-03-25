package client;


import client.protocol.MqttConnectOptions;
import client.utils.SessionUtils;
import io.netty.channel.Channel;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class SessionRegister {

    private NettyClientAccepter client;

    private Map<String, MqttConnectOptions> userSesionConnect = new ConcurrentHashMap<>();

    private Map<String, Session> sessionPool = new ConcurrentHashMap<>();

    private volatile Boolean aotuConnect = true;


    public SessionRegister() {
    }

    public void add(MqttConnectOptions options) throws InterruptedException {
        userSesionConnect.put(SessionUtils.getSessionKey(options),options);
        client.doConnect(options);
    }

    public void setClient(NettyClientAccepter client) {
        this.client = client;
    }

    public synchronized Session createNewOrUpdateSession(Channel channel){
        return new Session(channel);
    }



    public synchronized void remove(String ipPort) {
        userSesionConnect.remove(ipPort);
        Session remove = sessionPool.remove(ipPort);
        if (null != remove && null != remove.getChannel()) {
            remove.getChannel().close();
        }
    }


    public synchronized void setOnLineSessionKey(String onLineSessionKey) throws InterruptedException {
        if (aotuConnect) {
            MqttConnectOptions mqttConnectOptions = userSesionConnect.get(onLineSessionKey);
            if(null!=mqttConnectOptions){
                client.doConnect(mqttConnectOptions);
            }
        }
    }
}
