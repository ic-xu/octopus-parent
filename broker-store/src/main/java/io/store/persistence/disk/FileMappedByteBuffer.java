package io.store.persistence.disk;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.octopus.kernel.contants.BrokerConstants;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

public class FileMappedByteBuffer {

    private final MappedByteBuffer mappedByteBuffer;
    private final Long fileName;

    public FileMappedByteBuffer(String parentDir, Long fileStartPosition, int defaultMaxSize) throws IOException {
        this.fileName = fileStartPosition;
        File dataFile;
        if (null != parentDir) {
            dataFile = new File(BrokerConstants.DATA_QUEUE + parentDir + File.separator + fileStartPosition);
        } else {
            dataFile = new File(BrokerConstants.DATA_QUEUE + fileStartPosition);
        }

        if (!dataFile.getParentFile().exists()) {
            dataFile.getParentFile().mkdirs();
        }
        RandomAccessFile file = new RandomAccessFile(dataFile, "rw");
        mappedByteBuffer = file.getChannel().map(FileChannel.MapMode.READ_WRITE, 0, defaultMaxSize);
        file.close();
    }


    public FileMappedByteBuffer(RandomAccessFile file, Long fileName, long defaultMaxSize) throws IOException {
        this.fileName = fileName;
        mappedByteBuffer = file.getChannel().map(FileChannel.MapMode.READ_WRITE, 0, defaultMaxSize);
        file.close();
    }

    public static void removeFile(Long fileStartPosition) {
        File dataFile = new File(BrokerConstants.DATA_QUEUE + fileStartPosition);
        dataFile.deleteOnExit();

    }

    public void flush() {
        mappedByteBuffer.force();
    }

    public boolean remaining(int length) {
        return mappedByteBuffer.remaining() >= length;
    }

    public ByteBuffer appendByteArr(byte[] buff) {
        return mappedByteBuffer.put(buff);
    }


    public ByteBuffer appendInt(int value) {
        return mappedByteBuffer.putInt(value);
    }

    public void setWritePosition(int newPosition){
        mappedByteBuffer.position(newPosition);
    }

    public int getPosition(){
        return  mappedByteBuffer.position();
    }


    public int getLimit(){
        return mappedByteBuffer.limit();
    }

    public void setLimit(int newLimit){
         mappedByteBuffer.limit(newLimit);
    }



    public ByteBuf pollMessage(int position) {
        return readMessageBody(position);
    }


    private ByteBuf readMessageBody(int reallyIndex) {
        ByteBuffer duplicate = mappedByteBuffer.duplicate();
        duplicate.position(reallyIndex);
        int messageLength = duplicate.getInt();
        if (0 == messageLength) {
            return null;
        }
        duplicate.limit(duplicate.position()+messageLength);
        return Unpooled.wrappedBuffer(duplicate);
    }

    public ByteBuf readMessageBody(int reallyIndex,int messageLength) {
        ByteBuffer duplicate = mappedByteBuffer.duplicate();
        duplicate.position(reallyIndex);
        duplicate.limit(duplicate.position()+messageLength);
        return Unpooled.wrappedBuffer(duplicate);
    }

    public Long getFileName() {
        return fileName;
    }

}
