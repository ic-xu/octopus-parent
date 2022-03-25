package io.store.persistence.h2;


import io.octopus.base.queue.MsgIndex;
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
        if (obj instanceof MsgIndex) {
            final MsgIndex index = (MsgIndex) obj;
            /* Long => 8 , Int => 4, int => 4 */
            return 14;
        } else {
            logger.error("error queueMessageIndex index {} value", obj);
            return 0;
        }
    }

    @Override
    public void write(WriteBuffer buff, Object obj) {
        if (obj instanceof MsgIndex) {
            final MsgIndex index = (MsgIndex) obj;
            buff.putLong(index.getOffset());
            buff.putInt(index.getSize());
            buff.putInt(index.getQueueName());
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
        return new MsgIndex(offset, size, queueName);
    }

    @Override
    public void read(ByteBuffer buff, Object[] obj, int len, boolean key) {
        for (int i = 0; i < len; i++) {
            obj[i] = read(buff);
        }
    }
}
