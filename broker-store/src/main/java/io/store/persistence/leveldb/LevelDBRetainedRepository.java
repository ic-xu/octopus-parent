package io.store.persistence.leveldb;

import io.octopus.kernel.kernel.message.KernelMsg;
import io.octopus.kernel.kernel.repository.IRetainedRepository;
import io.octopus.kernel.kernel.subscriptions.RetainedMessage;
import io.octopus.kernel.kernel.subscriptions.Topic;
import org.iq80.leveldb.DB;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * @author chenxu
 * @version 1
 */
public class LevelDBRetainedRepository implements IRetainedRepository {

    private final DB db;

    private static final String RETAIN = "-retain";

    public LevelDBRetainedRepository(DB db) {
        this.db = db;
    }


    @Override
    public void cleanRetained(Topic topic) {
        db.delete((topic.getValue() + RETAIN).getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public boolean retain(Topic topic, KernelMsg msg) {
        return true;
    }

    @Override
    public boolean isEmpty() {
        return false;
    }

    @Override
    public List<RetainedMessage> retainedOnTopic(String topic) {
        return null;
    }
}
