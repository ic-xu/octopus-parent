package io.octopus.base.core;


import io.netty.channel.Channel;
import io.octopus.base.config.IConfig;
import io.octopus.base.interfaces.IAuthenticator;
import io.octopus.base.interfaces.IPostOffice;
import io.octopus.base.interfaces.ISessionResistor;
import io.octopus.base.interfaces.NotifyInterceptor;

import java.lang.reflect.InvocationTargetException;

public class ConnectionFactory {

    private IPostOffice postOffice;
    private ISessionResistor sessionResistor;
    private IAuthenticator authenticator;
    private IConfig config;
    private NotifyInterceptor interceptor;

    public ConnectionFactory(IPostOffice postOffice, ISessionResistor sessionResistor, IAuthenticator authenticator, IConfig config, NotifyInterceptor interceptor) {
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
                         NotifyInterceptor.class)
                .newInstance(channel,config,authenticator, sessionResistor, interceptor);
    }

}
