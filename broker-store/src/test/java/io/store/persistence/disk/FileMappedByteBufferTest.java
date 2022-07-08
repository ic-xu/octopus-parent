package io.store.persistence.disk;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

public class FileMappedByteBufferTest {

    private MappedByteBuffer mappedByteBuffer;
    @BeforeEach
    public void init() throws IOException {
        File dir = new File("data");
        if(!dir.exists()){
            dir.mkdirs();
        }
        RandomAccessFile file = new RandomAccessFile("data/0", "rw");
        int ca = 1024 * 1024 * 1024;
//        System.out.println(ca);
        mappedByteBuffer = file.getChannel().map(FileChannel.MapMode.READ_WRITE, 0, ca);

    }

    @Test
    public void setPosition() {

        System.out.println(mappedByteBuffer.position());
        System.out.println(mappedByteBuffer.limit());
        System.out.println(mappedByteBuffer.capacity());

        mappedByteBuffer.put((byte) 1);
        System.out.println(mappedByteBuffer.position());
        System.out.println(mappedByteBuffer.limit());
        System.out.println(mappedByteBuffer.capacity());

        mappedByteBuffer.put((byte) 2);
        System.out.println(mappedByteBuffer.position());
        System.out.println(mappedByteBuffer.limit());
        System.out.println(mappedByteBuffer.capacity());

        ByteBuffer slice = mappedByteBuffer.slice();

        System.out.println(slice.position());
        System.out.println(slice.limit());
        System.out.println(slice.capacity());
    }


    @Test
    public void mappedByteBuffer(){
        for (byte i = 0; i < 100; i++) {
            mappedByteBuffer.put(i);
        }


        ByteBuffer duplicate = mappedByteBuffer.duplicate();

        duplicate.position(10);
        System.out.println(duplicate.get());
        duplicate.limit(duplicate.position()+10);
        ByteBuf byteBuf = Unpooled.wrappedBuffer(duplicate);
        int capacity = byteBuf.capacity();
        byte[] bytes = new byte[capacity];
       byteBuf.readBytes(bytes);

        System.out.println("-----------------");
        for (int b = 0; b < bytes.length; b++) {
            System.out.println(bytes[b]);

        }


    }
}