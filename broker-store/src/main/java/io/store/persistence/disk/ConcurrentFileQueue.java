package io.store.persistence.disk;

import io.netty.buffer.ByteBuf;
import io.octopus.kernel.checkpoint.CheckPoint;
import io.octopus.contants.BrokerConstants;
import io.octopus.kernel.kernel.message.KernelPayloadMessage;
import io.octopus.kernel.kernel.queue.MsgIndex;
import io.octopus.kernel.kernel.queue.MsgQueue;
import io.octopus.kernel.kernel.queue.SearchData;
import io.octopus.kernel.kernel.queue.StoreMsg;
import io.octopus.kernel.utils.KernelMsgDecode;
import io.octopus.kernel.utils.KernelMsgEncode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @author user
 */ //TODO delete mappedByteBuffer
public class ConcurrentFileQueue implements MsgQueue<KernelPayloadMessage> {

    private final Logger logger = LoggerFactory.getLogger(ConcurrentFileQueue.class);
    private FileMappedByteBuffer currentWriteFileByteBuffer;
    private FileMappedByteBuffer currentReadFileByteBuffer;
    private final AtomicLong currentWritePosition = new AtomicLong(0);
    private final AtomicLong currentReadPosition = new AtomicLong(0);
    private final AtomicLong size = new AtomicLong(0);

    private final AtomicBoolean flip = new AtomicBoolean(false);
    private static final int DEFAULT_MAX_SIZE = 1024 * 1024 * 1024;
    private static final Long MAX_POSITION = Long.MAX_VALUE - (3L * DEFAULT_MAX_SIZE);
    private final AtomicLong currentMaxPosition = new AtomicLong(0);
    private int lastMessageSize = 0;
    private final TreeMap<Long, FileMappedByteBuffer> fileList;
    private final String parentDir;
    private final static String MODE = "rw";

    private final ReentrantLock writeLock = new ReentrantLock();
    private final ReentrantLock readLock = new ReentrantLock();
    private final CheckPointServer checkPointServer;


    private final AtomicBoolean stop = new AtomicBoolean(false);
    private final int threadNum;

    public ConcurrentFileQueue(String parentDir,int threadNum, CheckPointServer checkPointServer) throws IOException {
        this.parentDir = parentDir;
        this.threadNum = threadNum;
        fileList = new TreeMap<>();
        currentWriteFileByteBuffer = new FileMappedByteBuffer(parentDir, currentWritePosition.get(), DEFAULT_MAX_SIZE);
        this.checkPointServer = checkPointServer;
        init();
        loadCheckPoint();
    }

    public void flush() {
        currentWriteFileByteBuffer.flush();
    }


    @Override
    public int size() {
        return (int) size.get();
    }


    @Override
    public StoreMsg<KernelPayloadMessage> offer(KernelPayloadMessage msg) {
        if (stop.get()) {
            return null;
        }
        final byte[] messageArray = KernelMsgEncode.encode(msg);
        assert messageArray != null;
        writeLock.lock();
        try {
            if (!currentWriteFileByteBuffer.remaining(messageArray.length + 4)) {
                if (null != currentWriteFileByteBuffer) {
                    currentWriteFileByteBuffer.flush();
                }
                if (currentWritePosition.get() >= MAX_POSITION) {
                    currentWritePosition.set(0);
                    currentMaxPosition.set(currentWritePosition.get());
                    flip.set(true);
                }
                currentWriteFileByteBuffer = new FileMappedByteBuffer(parentDir, currentWritePosition.get(), DEFAULT_MAX_SIZE);
                fileList.put(currentWritePosition.get(), currentWriteFileByteBuffer);
            }
//            currentWriteFileByteBuffer.appendInt(messageArray.length);
            currentWriteFileByteBuffer.appendByteArr(messageArray);
            size.incrementAndGet();
            lastMessageSize = messageArray.length + 4;
            checkPointServer.saveCheckPoint(wrapperCheckPoint(), false);
            return new StoreMsg<>(msg, new MsgIndex(currentWritePosition.getAndAdd(lastMessageSize), messageArray.length, threadNum));
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
        finally {
            writeLock.unlock();
        }

    }


    @Override
    public CheckPoint wrapperCheckPoint() {
        CheckPoint checkPoint = new CheckPoint();
        checkPoint.setSaveTime(System.currentTimeMillis());
        checkPoint.setWriteLimit(getLimit());
        checkPoint.setWritePoint(getWritePosition());
        checkPoint.setReadPoint(getReadPosition());
        checkPoint.setPhysicalPoint(getPhysicalPosition());
        return checkPoint;
    }


    @Override
    public StoreMsg<KernelPayloadMessage> poll() {
        ByteBuf messageBuf;
        long messageIndex = currentReadPosition.get();
        readLock.lock();
        try {

            if (messageIndex >= currentWritePosition.get()) {
                return null;
            }
            if (fileList.containsKey(messageIndex)) {
                currentReadFileByteBuffer = fileList.get(messageIndex);
            }
            currentReadFileByteBuffer = findReallyFile(currentReadPosition.get());

            int phyIndex = (int) (messageIndex - currentReadFileByteBuffer.getFileName());
            messageBuf = currentReadFileByteBuffer.pollMessage(phyIndex);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
        finally {
            readLock.unlock();
        }

        if (null == messageBuf) {
            return null;
        }
        int messageSize = messageBuf.capacity();
        currentReadPosition.addAndGet(4 + messageSize);
        KernelPayloadMessage kernelPayloadMessage = null;
        try {
            kernelPayloadMessage = KernelMsgDecode.decode(messageBuf);
        } catch (Exception e) {
            System.out.println(messageBuf);
            e.printStackTrace();
        }

//        return new StoreMsg<>(mqttIMessage, new MsgIndex(messageIndex, messageSize, threadNum));
        return null;
    }


    @Override
    public StoreMsg<KernelPayloadMessage> poll(SearchData searchData) {
        FileMappedByteBuffer reallyFile = findReallyFile(searchData.getIndex().getOffset());
        int reallyPosition = (int) (searchData.getIndex().getOffset() - reallyFile.getFileName());
        if (reallyPosition > 0) {
//            ByteBuf messageBuf = reallyFile.pollMessage(reallyPosition);
            ByteBuf messageBuf = reallyFile.readMessageBody(reallyPosition,searchData.getIndex().getSize());
            if (messageBuf == null) {
                return null;
            }
            try {
                final int size = messageBuf.capacity();
                final KernelPayloadMessage kernelPayloadMessage = KernelMsgDecode.decode(messageBuf);
//                return new StoreMsg<>(mqttIMessage, new MsgIndex(searchData.getIndex().getOffset(), size, threadNum));
                return null;
            } catch (Exception e) {
                e.printStackTrace();
                logger.error("position is {} ,FileMappedByteBuffer is {}", searchData.getIndex().getOffset(), reallyFile.getFileName());
                logger.error("bytes length is {} ", messageBuf.capacity());
                System.exit(1);
            }
        }
        return null;
    }

    @Override
    public void flushDisk() {
        currentWriteFileByteBuffer.flush();
    }


    @Override
    public void init() throws FileNotFoundException {
        File dataDir;
        if (null != parentDir) {
            dataDir = new File(BrokerConstants.DATA_QUEUE + File.separator + parentDir);
        } else {
            dataDir = new File(BrokerConstants.DATA_QUEUE);
        }

        if (!dataDir.exists()) {
            boolean mkdirs = dataDir.mkdirs();
            if (!mkdirs) {
                throw new FileNotFoundException("create date dir error ...");
            }
        }
        File[] files = dataDir.listFiles();
        assert files != null;
        for (File f : files) {
            try {
                fileList.put(Long.parseLong(f.getName()), new FileMappedByteBuffer(new RandomAccessFile(f, MODE), Long.parseLong(f.getName()), DEFAULT_MAX_SIZE));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }


    private void loadCheckPoint() {
        CheckPoint checkPoint = checkPointServer.readCheckPoint();
        if (null != checkPoint) {
            currentReadPosition.set(checkPoint.getReadPoint());
//            currentReadPosition.set(1067558518);
            currentWritePosition.set(checkPoint.getWritePoint());
            currentWriteFileByteBuffer = findReallyFile(currentWritePosition.get());
            currentReadFileByteBuffer = findReallyFile(currentReadPosition.get());

            currentWriteFileByteBuffer.setWritePosition((int) (currentWritePosition.get() - currentWriteFileByteBuffer.getFileName()));
            logger.info("load checkpoint success !!! ,this checkPoint is {}", checkPoint);
        }
    }


    private FileMappedByteBuffer findReallyFile(long position) {
        if (flip.get() && currentMaxPosition.get() > 0 && position >= currentMaxPosition.get()) {
            flip.set(false);
            currentMaxPosition.set(0);
            currentReadPosition.set(0);
        }
        long fileKey = 0;
        for (Long fileName : fileList.keySet()) {
            if (position >= fileName) {
                fileKey = fileName;
            } else {
                break;
            }
        }
        return fileList.get(fileKey);
    }


    public Long getWritePosition() {
        return currentWritePosition.get();
    }

    public Long getReadPosition() {
        return currentReadPosition.get();
    }


    public void setWritePosition(int newPosition) {
        currentWriteFileByteBuffer.setWritePosition(newPosition);
    }

    public int getPhysicalPosition() {
        return (int) (currentWritePosition.get() - currentWriteFileByteBuffer.getFileName());
    }


    public int getLimit() {
        return currentWriteFileByteBuffer.getLimit();
    }


    @Override
    public void stop() {
        stop.set(true);
    }

    @Override
    public void destroy() {

    }
}
