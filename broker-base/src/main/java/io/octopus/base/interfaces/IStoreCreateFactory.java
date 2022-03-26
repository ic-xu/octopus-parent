package io.octopus.base.interfaces;

/**
 * a factory for create Repository
 *
 * @author chenxu
 * @version 1
 */
public interface IStoreCreateFactory {

    /**
     * @return
     */
    IQueueRepository createIQueueRepository();


    IRetainedRepository createIRetainedRepository();


    ISubscriptionsRepository createISubscriptionsRepository();


    IRouterRegister createIRouterRegister();
}
