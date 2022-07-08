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
public class MsgIndex {

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


    public MsgIndex(Long offset, Integer size, int queueName) {
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


    public static MsgIndex fromBytes(byte[] bytes) {
        final long offset = ByteUtils.bytes2Long(bytes, 0);
        final int size = ByteUtils.byteArray2Int(bytes, 8);
//        final String queueName = new String(Arrays.copyOfRange(bytes, 12, bytes.length), StandardCharsets.UTF_8);
        final int queueName = ByteUtils.byteArray2Short(bytes, 12);
        return new MsgIndex(offset, size, queueName);
    }

    public byte[] toBytes() {
        byte[] bytes = new byte[14];
        ByteUtils.long2Bytes(bytes, offset);
        ByteUtils.int2byte(bytes, size, 8);
        ByteUtils.short2byte(bytes,queueName,12);
        return bytes;
    }

//    public byte[] toBytes() {
//        byte[] payload = this.queueName.getBytes(StandardCharsets.UTF_8);
//        byte[] offsetBytes = ByteUtils.long2Bytes(offset);
//        byte[] sizeBytes = ByteUtils.int2byte(size);
//        byte[] bytes = new byte[payload.length + offsetBytes.length + sizeBytes.length];
//        System.arraycopy(offsetBytes, 0, bytes, 0, 8);
//        System.arraycopy(sizeBytes, 0, bytes, 8, 4);
//        System.arraycopy(payload, 0, bytes, 12, payload.length);
//        return bytes;
//    }

    @Override
    public String toString() {
        return "QueueMessageIndex{" +
                "offset=" + offset +
                ", size=" + size +
                ", queueName='" + queueName + '\'' +
                '}';
    }
}
