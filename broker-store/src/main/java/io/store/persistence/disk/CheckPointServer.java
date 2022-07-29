package io.store.persistence.disk;

import io.octopus.kernel.checkpoint.CheckPoint;
import io.octopus.contants.BrokerConstants;
import io.octopus.kernel.utils.ObjectUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

/**
 * checkpoint time
 * read position
 * write position
 * write limit
 * @author user
 */
public class CheckPointServer {

    Logger logger = LoggerFactory.getLogger(this.getClass());

    private final MappedByteBuffer checkPointBuffer;

    public CheckPointServer() throws IOException {
        File file = new File(BrokerConstants.DATA_CHECK_POINT);
        if (!file.exists()) {
            file.getParentFile().mkdirs();
        }
        RandomAccessFile randomAccessFile = new RandomAccessFile(file, "rw");
        checkPointBuffer = randomAccessFile.getChannel().map(FileChannel.MapMode.READ_WRITE, 0, 1024 * 1024 * 4);
        randomAccessFile.close();
    }


    public synchronized void saveCheckPoint(CheckPoint checkPoint, boolean isFlush) {
        if (ObjectUtils.isEmpty(checkPoint)) {
            // TODO
            logger.error("checkPoint is null ...");
        } else if (isFlush) {
            checkPointBuffer.force();
        } else {
            checkPointBuffer.position(0);
            checkPointBuffer.putLong(System.currentTimeMillis());
            checkPointBuffer.putLong(checkPoint.getReadPoint());
            checkPointBuffer.putLong(checkPoint.getWritePoint());
            checkPointBuffer.putInt(checkPoint.getWriteLimit());
            checkPointBuffer.putInt(checkPoint.getPhysicalPoint());
        }

    }


    public synchronized CheckPoint readCheckPoint() {
        CheckPoint checkPoint = new CheckPoint();
        checkPointBuffer.position(0);
        checkPoint.setSaveTime(checkPointBuffer.getLong());
        checkPoint.setReadPoint(checkPointBuffer.getLong());
        checkPoint.setWritePoint(checkPointBuffer.getLong());
        checkPoint.setWriteLimit(checkPointBuffer.getInt());
        checkPoint.setPhysicalPoint(checkPointBuffer.getInt());
        return checkPoint;
    }
}
