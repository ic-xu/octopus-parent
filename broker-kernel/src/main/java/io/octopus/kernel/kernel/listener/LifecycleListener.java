package io.octopus.kernel.kernel.listener;

import io.octopus.kernel.kernel.ILifecycle;

/**
 * @author chenxu
 * @version 1
 * @date 2022/6/21 17:56
 */
public interface LifecycleListener {


    /**
     * 初始化前
     * @param ILifecycle lifecycle
     * @throws Exception 异常
     */
    void beforeInit(ILifecycle ILifecycle)throws Exception;

    /**
     * 初始化之后
     * @param ILifecycle lifecycle
     * @throws Exception 异常
     */
    void afterInit(ILifecycle ILifecycle)throws Exception;

    /**
     * 开始之前
     * @param ILifecycle lifecycle
     * @throws Exception 异常
     */
    void beforeStart(ILifecycle ILifecycle)throws Exception;

    /**
     * 开始之后
     * @param ILifecycle lifecycle
     * @throws Exception 异常
     */
    void afterStart(ILifecycle ILifecycle)throws Exception;

    /**
     * 停止前
     * @param ILifecycle lifecycle
     * @throws Exception 异常
     */
    void beforeStop(ILifecycle ILifecycle)throws Exception;

    /***
     * 停止之后
     * @param ILifecycle lifecycle
     * @throws Exception 异常
     */
    void afterStop(ILifecycle ILifecycle)throws Exception;


    /**
     * 销毁之前
     * @param ILifecycle lifecycle
     * @throws Exception 异常
     */
    void beforeDestroy(ILifecycle ILifecycle)throws Exception;

    /***
     * 销毁之后
     * @param ILifecycle lifecycle
     * @throws Exception 异常
     */
    void afterDestroy(ILifecycle ILifecycle)throws Exception;
}
