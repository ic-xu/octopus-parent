package io.octopus.kernel.kernel.repository;

import io.octopus.kernel.kernel.queue.Index;

import java.io.IOException;

public interface IMsgQueue<T> {


    /**
     * 检索消息
     * @param index 索引
     * @return 消息，如果索引不存在，返回null
     */
    T retrievalKernelMsg(Index index);


    /**
     * 存储消息
     * @param kernelMessage 消息
     * @return 索引信息
     */
    Index storeMsg(T kernelMessage) throws IOException;


}
