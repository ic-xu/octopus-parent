package io.octopus.scala.broker.mqtt.factory

import io.netty.channel.Channel
import io.octopus.kernel.kernel.config.BrokerConfiguration
import io.octopus.kernel.kernel.interceptor.NotifyInterceptor
import io.octopus.kernel.kernel.postoffice.IPostOffice
import io.octopus.kernel.kernel.security.{IAuthenticator, ReadWriteControl}
import io.octopus.kernel.kernel.session.ISessionResistor
import io.octopus.scala.broker.mqtt.server.MQTTConnection

/**
 * connection create Factory
 * @param brokerConfig config
 * @param authenticator auth
 * @param sessionFactory sessionRegister
 * @param postOffice postOffice
 * @param interceptor interceptor
 */
class MQTTConnectionFactory(brokerConfig: BrokerConfiguration, authenticator: IAuthenticator, sessionFactory: ISessionResistor,
                            postOffice: IPostOffice, interceptor: NotifyInterceptor, readWriteControl:ReadWriteControl){

  def create(channel: Channel): MQTTConnection = new MQTTConnection(channel,brokerConfig,authenticator, sessionFactory,interceptor)

}
