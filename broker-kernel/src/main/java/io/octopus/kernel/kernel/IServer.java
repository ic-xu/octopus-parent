package io.octopus.kernel.kernel;

import io.octopus.kernel.kernel.listener.LifecycleListener;

import java.util.List;

/**
 * @author chenxu
 * @version 1
 * @date 2022/6/21 17:45
 */
public interface IServer extends Lifecycle {


    /**
     * 服务详细信息
     *
     * @return
     */
    Object serviceDetails();

    /**
     * 获取生命周期监听器
     * @return 返回一个list数组
     */
    List<LifecycleListener> lifecycleListenerList();

}
