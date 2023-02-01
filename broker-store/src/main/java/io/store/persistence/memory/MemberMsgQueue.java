package io.store.persistence.memory;

import io.octopus.kernel.kernel.message.IMessage;
import io.octopus.kernel.kernel.queue.Index;
import io.octopus.kernel.kernel.repository.IMsgQueue;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MemberMsgQueue<T extends IMessage> implements IMsgQueue<T> {

    Map<Index, T> store = new ConcurrentHashMap<>();

    @Override
    public T retrievalKernelMsg(Index index) {
        return store.get(index);
    }

    @Override
    public Index storeMsg(T msg) throws IOException {
        Index index = new Index(System.currentTimeMillis(), msg.getSize(), 0,msg.getQos(), msg.messageId());
        store.put(index, msg);
        return index;
    }
}
