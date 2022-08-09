package io.octopus.kernel.kernel.queue;

import io.netty.util.internal.ObjectUtil;
import io.octopus.kernel.utils.ByteUtils;

/**
 * An index file built for each message is in a user session queue
 * <p>
 * offset  is the starting position of the message  in the whole queue system
 * size is the message length
 * queueName is a queue system Name
 *
 * @author chenxu
 * @version 1
 */
public class Index {

    /**
     * offset  is the starting position of the message  in the whole queue system
     */
    private final Long offset;

    /**
     * size is the message length,use Interger description a length ,the max is  Integer.MAX_VALUE
     */
    private final Integer size;

    /**
     * queueName is a queue system Name,use a short number description a name of ths queue system
     */
    private final int queueName;


    public Index(Long offset, Integer size, int queueName) {
        ObjectUtil.checkNotNull(queueName, "queueName not null");
        ObjectUtil.checkInRange(offset, 0L, Long.MAX_VALUE, "offset must >= 0 && <= Long.MAX_VALUE");
        this.offset = offset;
        this.size = size;
        this.queueName = queueName;
    }

    public Integer getSize() {
        return size;
    }

    public Long getOffset() {
        return offset;
    }

    public int getQueueName() {
        return queueName;
    }


    public static Index fromBytes(byte[] bytes) {
        final long offset = ByteUtils.bytes2Long(bytes, 0);
        final int size = ByteUtils.byteArray2Int(bytes, 8);
        final int queueName = ByteUtils.byteArray2Short(bytes, 12);
        return new Index(offset, size, queueName);
    }

    public byte[] toBytes() {
        byte[] bytes = new byte[14];
        ByteUtils.long2Bytes(bytes, offset);
        ByteUtils.int2byte(bytes, size, 8);
        ByteUtils.short2byte(bytes,queueName,12);
        return bytes;
    }


    @Override
    public String toString() {
        return "QueueMessageIndex{" +
                "offset=" + offset +
                ", size=" + size +
                ", queueName='" + queueName + '\'' +
                '}';
    }
}
