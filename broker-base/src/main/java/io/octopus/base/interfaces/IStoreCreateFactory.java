package io.octopus.base.interfaces;

/**
 * @author chenxu
 * @version 1
 */
public interface IStoreCreateFactory {

    /**
     *
     * @return
     */
   IQueueRepository createIQueueRepository() ;


    IRetainedRepository createIRetainedRepository();


    ISubscriptionsRepository createISubscriptionsRepository() ;


    IRouterRegister createIRouterRegister() ;
}
