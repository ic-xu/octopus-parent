package io.octopus.kernel.kernel.queue;

import io.netty.util.internal.ObjectUtil;
import io.octopus.kernel.kernel.message.MsgQos;
import io.octopus.kernel.utils.ByteUtils;

import java.io.Serializable;
import java.util.Objects;

/**
 * An index file built for each message is in a user session queue
 * <p>
 * offset  is the starting position of the message  in the whole queue system
 * size is the message length
 * queueName is a queue system Name
 *
 * @param offset    offset  is the starting position of the message  in the whole queue system
 * @param size      size is the message length,use Interger description a length ,the max is  Integer.MAX_VALUE
 * @param queueName queueName is a queue system Name,use a short number description a name of ths queue system
 * @author chenxu
 * @version 1
 */
public record Index(Long offset, Integer size, int queueName, MsgQos qos,Long messageId) implements Serializable {

    public Index {
        ObjectUtil.checkNotNull(queueName, "queueName not null");
        ObjectUtil.checkInRange(offset, -1L, Long.MAX_VALUE, "offset must >= 0 && <= Long.MAX_VALUE");
    }


    public static Index fromBytes(byte[] bytes) {
        final long offset = ByteUtils.bytes2Long(bytes, 0);
        final int size = ByteUtils.byteArray2Int(bytes, 8);
        final int queueName = ByteUtils.byteArray2Short(bytes, 12);
        final MsgQos qos = MsgQos.valueOf(bytes[14]);
        final long messageId = ByteUtils.bytes2Long(bytes, 15);
        return new Index(offset, size, queueName, qos,messageId);
    }

    public byte[] toBytes() {
        byte[] bytes = new byte[19];
        ByteUtils.long2Bytes(bytes, offset);
        ByteUtils.int2byte(bytes, size, 8);
        ByteUtils.short2byte(bytes, queueName, 12);
        bytes[14] = (byte) qos.getValue();
        ByteUtils.long2Bytes(bytes, messageId);
        return bytes;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Index index = (Index) o;
        return queueName == index.queueName && offset.equals(index.offset) && size.equals(index.size) && messageId.equals(index.messageId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(offset, size, queueName);
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
