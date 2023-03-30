package io.store.persistence.leveldb;

import io.octopus.kernel.kernel.queue.Index;
import io.octopus.kernel.utils.ByteUtils;
import io.octopus.kernel.utils.ObjectUtils;
import org.iq80.leveldb.DB;
import org.iq80.leveldb.WriteOptions;

import java.nio.charset.StandardCharsets;
import java.util.AbstractQueue;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author chenxu
 * @version 1
 */
public class LevelDBPersistentQueue extends AbstractQueue<Index> {


    private final AtomicLong queueHead;

    private final AtomicLong queueTail;

    private final static String HEAD_STRING = "-head";
    private final static String TAIL_STRING = "-tail";

    private final String clientId;

    private final AtomicInteger size = new AtomicInteger(0);
    private final WriteOptions writeOptions = new WriteOptions();

    private final DB db;

    public LevelDBPersistentQueue(DB db, String clientId) {
        this.db = db;
        this.clientId = clientId;
        byte[] head = getQueueHead();
        if (ObjectUtils.isEmpty(head)) {
            this.queueHead = new AtomicLong(0);
        } else {
            this.queueHead = new AtomicLong(ByteUtils.bytes2Long(head));
        }
        byte[] tailBytes = getQueueTail();
        if (ObjectUtils.isEmpty(tailBytes)) {
            this.queueTail = new AtomicLong(0);
        } else {
            this.queueTail = new AtomicLong(ByteUtils.bytes2Long(tailBytes));
        }
    }


    /**
     * get queue head
     *
     * @return byte
     */
    private byte[] getQueueHead() {
        return (clientId + HEAD_STRING).getBytes(StandardCharsets.UTF_8);
    }


    /**
     * get queue head
     *
     * @return byte
     */
    private byte[] getQueueTail() {
        return (clientId + TAIL_STRING).getBytes(StandardCharsets.UTF_8);
    }


    /**
     * get queue head
     *
     * @return byte
     */
    private byte[] getQueueTailKey() {
        return (clientId + "-" + queueTail.incrementAndGet()).getBytes(StandardCharsets.UTF_8);
    }


    /**
     * get queue head
     *
     * @return byte
     */
    private byte[] getQueueTailKey(long value) {
        return (clientId + "-" + value).getBytes(StandardCharsets.UTF_8);
    }

    /**
     * get queue head
     *
     * @return byte
     */
    private byte[] getQueueHeadKey() {
        return (clientId + "-" + queueHead.incrementAndGet()).getBytes(StandardCharsets.UTF_8);
    }


    @Override
    public Iterator<Index> iterator() {
        return null;
    }

    @Override
    public int size() {
        return size.get();
    }

    @Override
    public boolean offer(Index index) {
        db.put(getQueueHeadKey(), index.toBytes(), writeOptions);
        size.incrementAndGet();
        return true;
    }

    @Override
    public Index poll() {
        if (queueTail.get() >= queueHead.get()) {
            return null;
        }
        byte[] queueKey = getQueueTailKey();
        byte[] bytes = db.get(queueKey);
        if (ObjectUtils.isEmpty(bytes)) {
            return null;
        } else {
            db.delete(queueKey);
        }
        size.decrementAndGet();
        return Index.fromBytes(bytes);
    }

    @Override
    public Index peek() {
        if (queueTail.get() >= queueHead.get()) {
            return null;
        }
        byte[] bytes = db.get(getQueueTailKey(queueTail.get()));
        if (ObjectUtils.isEmpty(bytes)) {
            return null;
        }
        return Index.fromBytes(bytes);
    }


    @Override
    public void clear() {
        while (queueTail.get() <= queueHead.get()) {
           final byte[] queueTailKey = getQueueTailKey();
            db.delete(queueTailKey);
        }
    }
}
