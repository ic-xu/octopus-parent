package io.store.persistence.disk;

import io.octopus.base.queue.MsgIndex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.BitSet;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author chenxu
 * @version 1
 */
public class IndexFile {
    Logger logger = LoggerFactory.getLogger(IndexFile.class);

    /* 1024 * 1024 * 1204 * 1.5 = 1.5G*/
    private final int indexFileSize = 1024 * 1024 * 1536;

    //8*8*2
    private final int indexCount = indexFileSize / 128;
    private BitSet bitSet = new BitSet(indexCount);
    private MappedByteBuffer mappedByteBuffer;

    public IndexLinkedQueue createQueue() {
        return new IndexLinkedQueue(this);
    }

    private AtomicBoolean initState = new AtomicBoolean(false);


    public void init() {
        try {
            mappedByteBuffer = new RandomAccessFile("", "rw").getChannel().map(FileChannel.MapMode.READ_WRITE, 0, indexFileSize);
            initState.compareAndSet(false, true);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void writeIndex(MsgIndex msgIndex) {
        int writeOffset = findNotWriteBlock() * 64;
        mappedByteBuffer.putLong(writeOffset, msgIndex.getOffset());
        mappedByteBuffer.putLong(writeOffset + 64, msgIndex.getSize());
        mappedByteBuffer.putLong(writeOffset + 96, msgIndex.getQueueName());
    }


    public int findNotWriteBlock() {
        long[] longs = bitSet.toLongArray();
        int notLongIndex = -1;
        for (int i = 0; i < longs.length; i++) {
            if (longs[i] < 64) {
                notLongIndex = i;
            }
        }
        if (notLongIndex < 0) {
            logger.error("notLongIndex error");
        }
        int index = 0;
        for (int i = notLongIndex * 64; i < (notLongIndex + 1) * 64; i++) {
            if (!bitSet.get(i)) {
                bitSet.set(i);
                index = i;
                break;
            }
        }
        return index;
    }
}
