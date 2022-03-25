package io.octopus.scala.factory

import io.handler.codec.mqtt.IMessage
import io.netty.channel.Channel
import io.octopus.broker.security.ReadWriteControl
import io.octopus.base.config.BrokerConfiguration
import io.octopus.base.interfaces.IAuthenticator
import io.octopus.base.queue.MsgQueue
import io.octopus.interception.BrokerNotifyInterceptor
import io.octopus.scala.broker.session.SessionResistor
import io.octopus.scala.broker.{MQTTConnection, PostOffice}

/**
 * connection create Factory
 * @param brokerConfig config
 * @param authenticator auth
 * @param sessionFactory sessionRegister
 * @param postOffice postOffice
 * @param interceptor interceptor
 */
class MQTTConnectionFactory(brokerConfig: BrokerConfiguration, authenticator: IAuthenticator, sessionFactory: SessionResistor,
                            postOffice: PostOffice, interceptor: BrokerNotifyInterceptor, readWriteControl:ReadWriteControl, msgQueue:MsgQueue[IMessage]){

  def create(channel: Channel): MQTTConnection = new MQTTConnection(channel,brokerConfig,authenticator,
    sessionFactory,postOffice,readWriteControl,msgQueue:MsgQueue[IMessage],interceptor)

}
