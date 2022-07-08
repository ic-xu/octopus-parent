package io.octopus.scala.broker.mqtt.factory

import io.netty.channel.Channel
import io.octopus.broker.security.ReadWriteControl
import io.octopus.kernel.kernel.config.BrokerConfiguration
import io.octopus.kernel.kernel.interceptor.NotifyInterceptor
import io.octopus.kernel.kernel.security.IAuthenticator
import io.octopus.kernel.kernel.session.ISessionResistor
import io.octopus.scala.broker.mqtt.server.{MQTTConnection, PostOffice}

/**
 * connection create Factory
 * @param brokerConfig config
 * @param authenticator auth
 * @param sessionFactory sessionRegister
 * @param postOffice postOffice
 * @param interceptor interceptor
 */
class MQTTConnectionFactory(brokerConfig: BrokerConfiguration, authenticator: IAuthenticator, sessionFactory: ISessionResistor,
                            postOffice: PostOffice, interceptor: NotifyInterceptor, readWriteControl:ReadWriteControl){

  def create(channel: Channel): MQTTConnection = new MQTTConnection(channel,brokerConfig,authenticator, sessionFactory,interceptor)

}
