package io.octopus.kernel.kernel.repository;

import io.octopus.kernel.kernel.lifecycle.Lifecycle;
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
   IQueueRepository createIQueueRepository() ;


    IRetainedRepository createIRetainedRepository();


    ISubscriptionsRepository createISubscriptionsRepository() ;


    IRouterRegister createIRouterRegister() ;
}
