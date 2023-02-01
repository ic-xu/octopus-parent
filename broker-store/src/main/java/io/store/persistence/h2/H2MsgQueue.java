
package io.store.persistence.h2;

import io.octopus.kernel.kernel.message.IMessage;
import io.octopus.kernel.kernel.queue.Index;
import io.octopus.kernel.kernel.repository.IMsgQueue;
import io.octopus.kernel.utils.ObjectUtils;
import org.h2.mvstore.MVMap;
import org.h2.mvstore.MVStore;

import java.io.IOException;

public class H2MsgQueue<T extends IMessage> implements IMsgQueue<T> {


    private final MVMap<Index, T> messageMap;

    private final MVMap<String, Long> metadataMap;

    private final static String metaString = "index";

    private final static String  queueName = "all-msg-queue";

    public H2MsgQueue(MVStore mvStore) {
        this.metadataMap = mvStore.openMap( queueName + "_meta");
        final MVMap.Builder<Index, T> messageTypeBuilder =
                new MVMap.Builder<Index, T>()
                        .valueType(new QueueMessageIndexDataType());
        this.messageMap = mvStore.openMap(queueName, messageTypeBuilder);
    }

    @Override
    public T retrievalKernelMsg(Index index) {
        return messageMap.get(index);
    }

    @Override
    public Index storeMsg(T msg) throws IOException {
        Long aLong = metadataMap.get(metaString);
        if(ObjectUtils.isEmpty(aLong)){
            aLong = 0L;
        }
        metadataMap.put(metaString,++aLong);
        Index index = new Index(aLong,msg.getSize(),0,msg.getQos(),msg.messageId());
        T put = messageMap.put(index, msg);
        return index;
    }
}
