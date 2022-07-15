package io.octopus.kernel.kernel.connect;


import io.netty.channel.Channel;
import io.octopus.kernel.kernel.config.IConfig;
import io.octopus.kernel.kernel.interceptor.ConnectionNotifyInterceptor;
import io.octopus.kernel.kernel.IPostOffice;
import io.octopus.kernel.kernel.security.IAuthenticator;
import io.octopus.kernel.kernel.ISessionResistor;

import java.lang.reflect.InvocationTargetException;

public class ConnectionFactory {

    private IPostOffice postOffice;
    private ISessionResistor sessionResistor;
    private IAuthenticator authenticator;
    private IConfig config;
    private ConnectionNotifyInterceptor interceptor;

    public ConnectionFactory(IPostOffice postOffice, ISessionResistor sessionResistor, IAuthenticator authenticator, IConfig config, ConnectionNotifyInterceptor interceptor) {
        this.postOffice = postOffice;
        this.sessionResistor = sessionResistor;
        this.authenticator = authenticator;
        this.config = config;
        this.interceptor = interceptor;
    }

    /*

        public AbstractConnection(Channel channel, BrokerConfiguration brokerConfig, IAuthenticator authenticator,
                              ISessionResistor sessionResistor, NotifyInterceptor interceptor,
                              IRWController readWriteControl) {

     */

    public AbstractConnection createConnection(Channel channel, Class<?> clazz)
            throws NoSuchMethodException, InvocationTargetException,
            InstantiationException, IllegalAccessException {
        return clazz.asSubclass(AbstractConnection.class)
                .getDeclaredConstructor(Channel.class, IConfig.class,IAuthenticator.class, ISessionResistor.class,
                         ConnectionNotifyInterceptor.class)
                .newInstance(channel,config,authenticator, sessionResistor, interceptor);
    }

}
