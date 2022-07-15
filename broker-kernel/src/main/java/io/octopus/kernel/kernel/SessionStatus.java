package io.octopus.kernel.kernel;

/**
 * @author chenxu
 * @version 1
 * @date 2022/6/21 19:44
 */
public enum SessionStatus {
    /**
     * 已经连接
     */
    CONNECTED,

    /**
     * 连接建立中
     */
    CONNECTING,

    /**
     * 断开连接中
     */
    DISCONNECTING,

    /**
     * 已经断开连接
     */
    DISCONNECTED;
}
