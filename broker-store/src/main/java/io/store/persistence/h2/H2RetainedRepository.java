package io.store.persistence.h2;

import io.netty.buffer.ByteBuf;
import io.netty.util.ReferenceCountUtil;
import io.octopus.kernel.kernel.message.KernelMsg;
import io.octopus.kernel.kernel.repository.IRetainedRepository;
import io.octopus.kernel.kernel.subscriptions.RetainedMessage;
import io.octopus.kernel.kernel.subscriptions.Topic;
import org.h2.mvstore.MVMap;
import org.h2.mvstore.MVStore;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * @author user
 */
public class H2RetainedRepository implements IRetainedRepository {

    private final MVMap<Topic, Set<RetainedMessage>> queueMap;

    public H2RetainedRepository(MVStore mvStore) {
        this.queueMap = mvStore.openMap("retained_store");
    }

    @Override
    public void cleanRetained(Topic topic) {
        queueMap.remove(topic);
    }


    @Override
    public boolean retain(Topic topic, KernelMsg msg) {
        final ByteBuf payload = msg.getPayload().copy();
        byte[] rawPayload = new byte[payload.readableBytes()];
        payload.getBytes(0, rawPayload);
        final RetainedMessage toStore = new RetainedMessage(topic, msg.getQos(), rawPayload);
        Set<RetainedMessage> messageSet = queueMap.computeIfAbsent(topic, (key) -> new CopyOnWriteArraySet<>());
        boolean result = messageSet.add(toStore);
        ReferenceCountUtil.safeRelease(msg);
        return result;
    }

    @Override
    public boolean isEmpty() {
        return queueMap.isEmpty();
    }

    @Override
    public List<RetainedMessage> retainedOnTopic(String topic) {
        final Topic searchTopic = new Topic(topic);
        final List<RetainedMessage> matchingMessages = new ArrayList<>();
        for (Map.Entry<Topic, Set<RetainedMessage>> entry : queueMap.entrySet()) {
            final Topic scanTopic = entry.getKey();
            if (scanTopic.match(searchTopic)) {
                matchingMessages.addAll(entry.getValue());
            }
        }
        return matchingMessages;
    }
}
