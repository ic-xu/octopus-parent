package io.store.persistence.leveldb;

import io.handler.codec.mqtt.MqttPublishMessage;
import io.octopus.base.interfaces.IRetainedRepository;
import io.octopus.base.subscriptions.RetainedMessage;
import io.octopus.base.subscriptions.Topic;
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
    public void retain(Topic topic, MqttPublishMessage msg) {

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
