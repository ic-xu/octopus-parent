package io.store.persistence.h2;


import io.octopus.kernel.kernel.message.MsgQos;
import io.octopus.kernel.kernel.queue.Index;
import org.h2.mvstore.WriteBuffer;
import org.h2.mvstore.type.StringDataType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;

public final class QueueMessageIndexDataType implements org.h2.mvstore.type.DataType {

    final Logger logger = LoggerFactory.getLogger(this.getClass());

    private final StringDataType stringDataType = new StringDataType();

    @Override
    public int compare(Object a, Object b) {
        return 0;
    }

    @Override
    public int getMemory(Object obj) {
        if (obj instanceof Index) {
            final Index index = (Index) obj;
            /* Long => 8 , Int => 4, int => 4 */
            return 14;
        } else {
            logger.error("error queueMessageIndex index {} value", obj);
            return 0;
        }
    }

    @Override
    public void write(WriteBuffer buff, Object obj) {
        if (obj instanceof Index) {
            final Index index = (Index) obj;
            buff.putLong(index.offset());
            buff.putInt(index.size());
            buff.putInt(index.queueName());
//            stringDataType.write(buff, index.getQueueName());
        } else {
            throw new IllegalArgumentException("Unrecognized message class " + obj.getClass());
        }
    }

    @Override
    public void write(WriteBuffer buff, Object[] obj, int len, boolean key) {
        for (int i = 0; i < len; i++) {
            write(buff, obj[i]);
        }
    }

    @Override
    public Object read(ByteBuffer buff) {
        final long offset = buff.getLong();
        final int size = buff.getInt();
//        final String queueName = stringDataType.read(buff);
        final int queueName = buff.getInt();
        final MsgQos qos =MsgQos.valueOf( buff.get());
        final long messageId = buff.getLong();
        return new Index(offset, size, queueName,qos,messageId);
    }

    @Override
    public void read(ByteBuffer buff, Object[] obj, int len, boolean key) {
        for (int i = 0; i < len; i++) {
            obj[i] = read(buff);
        }
    }
}
