package io.octopus.kernel.kernel.connect;

import io.netty.channel.Channel;
import io.octopus.kernel.config.BrokerConfiguration;
import io.octopus.kernel.kernel.interceptor.ConnectionNotifyInterceptor;
import io.octopus.kernel.kernel.message.KernelMessage;
import io.octopus.kernel.kernel.security.IAuthenticator;
import io.octopus.kernel.kernel.ISession;
import io.octopus.kernel.kernel.ISessionResistor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.List;

/**
 * @author chenxu
 * @version 1
 * @date 2022/1/21 4:54 下午
 */
public abstract class AbstractConnection {

    private Logger logger = LoggerFactory.getLogger(AbstractConnection.class);

    protected final Channel channel;
    protected final BrokerConfiguration brokerConfig;
    protected final IAuthenticator authenticator;
    protected final ISessionResistor sessionResistor;
    private volatile boolean connected;
    protected final List<ConnectionNotifyInterceptor> interceptors;

    public AbstractConnection(Channel channel, BrokerConfiguration brokerConfig, IAuthenticator authenticator,
                              ISessionResistor sessionResistor, List<ConnectionNotifyInterceptor> interceptors) {
        this.channel = channel;
        this.brokerConfig = brokerConfig;
        this.authenticator = authenticator;
        this.sessionResistor = sessionResistor;
        this.connected = false;
        this.interceptors = interceptors;
    }

    /**
     * 发送消息，如果链接没有断开的话
     *
     * @param msg
     */
    public abstract Boolean sendIfWritableElseDrop(KernelMessage msg) ;


//    /**
//     * send any message
//     *
//     * @param msg
//     */
//    public abstract void sendAnyIfWritableElseDrop(Object msg);


    /**
     * bound session for the channel
     *
     * @return
     */
    public abstract ISession session();

    /**
     * connectionLost ,call the methon
     */
    public abstract void handleConnectionLost();

    /**
     * 丢失连接
     */
    public abstract void dropConnection();


    public Channel getChannel() {
        return channel;
    }

    /**
     * remote Address
     *
     * @return ss
     */
    public InetSocketAddress remoteAddress() {
        return (InetSocketAddress) channel.remoteAddress();
    }

    /**
     * flush channel message
     */
    public void flush() {
        channel.flush();
    }

    public abstract void readCompleted();


    /**
     * 触发写超时,如果超过一定时间不写入，就触发这个方法
     */
    public  void writeTimeOut(){

    }
}
