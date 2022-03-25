package io.octopus.base.core;

import io.netty.channel.Channel;
import io.octopus.base.config.BrokerConfiguration;
import io.octopus.base.interfaces.*;
import io.octopus.base.message.Message;

import java.net.InetSocketAddress;

/**
 * @author chenxu
 * @version 1
 * @date 2022/1/21 4:54 下午
 */
public abstract class AbstractConnection {

    protected final Channel channel;
    protected final BrokerConfiguration brokerConfig;
    protected final IAuthenticator authenticator;
    protected final ISessionResistor sessionResistor;
    private volatile boolean connected;
    protected final NotifyInterceptor interceptor;

    public AbstractConnection(Channel channel, BrokerConfiguration brokerConfig, IAuthenticator authenticator,
                              ISessionResistor sessionResistor, NotifyInterceptor interceptor) {
        this.channel = channel;
        this.brokerConfig = brokerConfig;
        this.authenticator = authenticator;
        this.sessionResistor = sessionResistor;
        this.connected = false;
        this.interceptor = interceptor;
    }

    /**
     * 发送消息，如果链接没有断开的话
     * @param msg
     */
    public abstract void sendIfWritableElseDrop(Message msg);

    /**
     * send any message
     * @param msg
     */
    public abstract void sendAnyIfWritableElseDrop(Object msg);

    /**
     * remote Address
     * @return ss
     */
    public abstract InetSocketAddress remoteAddress();

    /**
     * flush channel message
     */
    public abstract void flush();

    /**
     * bound session for the channel
     * @return
     */
    public abstract ISession session();

    /**
     * connectionLost ,call the methon
     */
    public abstract void handleConnectionLost();

    /**
     * remove channel
     * @param channel
     */
    public abstract void removeConnect(Channel channel);

}
