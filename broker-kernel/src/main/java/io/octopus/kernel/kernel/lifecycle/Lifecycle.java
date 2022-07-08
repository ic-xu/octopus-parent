package io.octopus.kernel.kernel.lifecycle;

/**
 * container life cycle
 *
 * @author user
 */
public interface Lifecycle {

    /**
     * 初始化方法
     *
     * @throws Exception e
     */
    void init() throws Exception;

    /**
     * start life or the container
     *
     * @throws Exception e
     */

    default void start() throws Exception {
    }

    /**
     * start life or the container
     *
     * @throws Exception e
     */
    default void start(String[] args) throws Exception {
    }


    /**
     * stop life or container
     *
     * @throws Exception e
     */
    default void stop() throws Exception {
    }

    /**
     * 方法销毁之后调用
     *
     * @throws Exception e
     */
    void destroy() throws Exception;

}
