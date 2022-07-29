//package io.octopus.broker.session;
//
//import io.octopus.base.message.IMessage;
//import io.netty.channel.Channel;
//import io.octopus.broker.MsgDispatcher;
//import io.octopus.kernel.kernel.security.ReadWriteControl;
//import io.octopus.base.interfaces.IAuthenticator;
//import io.octopus.base.config.BrokerConfiguration;
//import io.octopus.interception.BrokerNotifyInterceptor;
//import io.octopus.base.queue.MsgQueue;
//
///**
// * Connection factory
// * <p>
// * create connection for client
// */
//public class MqttConnectionFactory {
//
//    private final BrokerConfiguration brokerConfig;
//    private final IAuthenticator authenticator;
//    private final SessionRegsistor sessionRegistry;
//    private final MsgDispatcher msgDispatcher;
//    private final BrokerNotifyInterceptor interceptor;
//    private final ReadWriteControl readWriteControl;
//    private final MsgQueue<IMessage> concurrentFileQueue;
//
//   public MqttConnectionFactory(BrokerConfiguration brokerConfig, IAuthenticator authenticator,
//                                SessionRegsistor sessionRegistry, MsgDispatcher msgDispatcher,
//                                BrokerNotifyInterceptor interceptor, ReadWriteControl readWriteControl,
//                                MsgQueue<IMessage> concurrentFileQueue) {
//        this.brokerConfig = brokerConfig;
//        this.authenticator = authenticator;
//        this.sessionRegistry = sessionRegistry;
//        this.msgDispatcher = msgDispatcher;
//        this.interceptor = interceptor;
//        this.readWriteControl = readWriteControl;
//        this.concurrentFileQueue = concurrentFileQueue;
//    }
//
//    public MqttConnection create(Channel channel) {
//        return new MqttConnection(channel, brokerConfig, authenticator, sessionRegistry, msgDispatcher, interceptor, readWriteControl,concurrentFileQueue);
//    }
//}
