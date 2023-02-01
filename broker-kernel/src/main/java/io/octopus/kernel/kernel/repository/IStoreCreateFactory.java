package io.octopus.kernel.kernel.repository;

import io.octopus.kernel.kernel.Lifecycle;
import io.octopus.kernel.kernel.message.IMessage;
import io.octopus.kernel.kernel.router.IRouterRegister;

/**
 * @author chenxu
 * @version 1
 */
public interface IStoreCreateFactory extends Lifecycle {

    /**
     *
     * @return
     */
   IndexQueueFactory createIndexQueueRepository() ;


    IRetainedRepository createIRetainedRepository();


    ISubscriptionsRepository createISubscriptionsRepository() ;


    IRouterRegister createIRouterRegister() ;



    IMsgQueue<IMessage> createIMsgQueueRepository();
}
