package io.octopus.broker;

import io.octopus.broker.config.BrokerConfiguration;
import io.octopus.broker.security.IAuthenticator;
import io.octopus.interception.BrokerNotifyInterceptor;
import io.netty.channel.Channel;

/**
 * Connection factory
 *
 * create connection for client
 */
public class MqttConnectionFactory {

    private final BrokerConfiguration brokerConfig;
    private final IAuthenticator authenticator;
    private final SessionRegistry sessionRegistry;
    private final PostOffice postOffice;
    private final BrokerNotifyInterceptor interceptor;

    MqttConnectionFactory(BrokerConfiguration brokerConfig, IAuthenticator authenticator,
                          SessionRegistry sessionRegistry, PostOffice postOffice, BrokerNotifyInterceptor interceptor) {
        this.brokerConfig = brokerConfig;
        this.authenticator = authenticator;
        this.sessionRegistry = sessionRegistry;
        this.postOffice = postOffice;
        this.interceptor = interceptor;
    }

    public MqttConnection create(Channel channel) {
        return new MqttConnection(channel, brokerConfig, authenticator, sessionRegistry, postOffice,interceptor);
    }
}
