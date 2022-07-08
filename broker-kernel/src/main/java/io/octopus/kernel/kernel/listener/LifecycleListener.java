package io.octopus.kernel.kernel.listener;

import io.octopus.kernel.kernel.lifecycle.Lifecycle;

/**
 * @author chenxu
 * @version 1
 * @date 2022/6/21 17:56
 */
public interface LifecycleListener {


    /**
     * 初始化前
     * @param lifecycle lifecycle
     * @throws Exception 异常
     */
    void beforeInit(Lifecycle lifecycle)throws Exception;

    /**
     * 初始化之后
     * @param lifecycle lifecycle
     * @throws Exception 异常
     */
    void afterInit(Lifecycle lifecycle)throws Exception;

    /**
     * 开始之前
     * @param lifecycle lifecycle
     * @throws Exception 异常
     */
    void beforeStart(Lifecycle lifecycle)throws Exception;

    /**
     * 开始之后
     * @param lifecycle lifecycle
     * @throws Exception 异常
     */
    void afterStart(Lifecycle lifecycle)throws Exception;

    /**
     * 停止前
     * @param lifecycle lifecycle
     * @throws Exception 异常
     */
    void beforeStop(Lifecycle lifecycle)throws Exception;

    /***
     * 停止之后
     * @param lifecycle lifecycle
     * @throws Exception 异常
     */
    void afterStop(Lifecycle lifecycle)throws Exception;


    /**
     * 销毁之前
     * @param lifecycle lifecycle
     * @throws Exception 异常
     */
    void beforeDestroy(Lifecycle lifecycle)throws Exception;

    /***
     * 销毁之后
     * @param lifecycle lifecycle
     * @throws Exception 异常
     */
    void afterDestroy(Lifecycle lifecycle)throws Exception;
}
