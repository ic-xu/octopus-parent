package io.store.persistence.h2;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.util.ReferenceCountUtil;
import org.h2.mvstore.WriteBuffer;

import java.nio.ByteBuffer;

public final class ByteBufDataType implements org.h2.mvstore.type.DataType {

    @Override
    public int compare(Object a, Object b) {
        return 0;
    }

    @Override
    public int getMemory(Object obj) {
        if (!(obj instanceof ByteBuf)) {
            throw new IllegalArgumentException("Expected instance of ByteBuf but found " + obj.getClass());
        }
        final int payloadSize = ((ByteBuf) obj).readableBytes();
        return 4 + payloadSize;
    }

    @Override
    public void read(ByteBuffer buff, Object[] obj, int len, boolean key) {
        for (int i = 0; i < len; i++) {
            obj[i] = read(buff);
        }
    }

    @Override
    public void write(WriteBuffer buff, Object[] obj, int len, boolean key) {
        for (int i = 0; i < len; i++) {
            write(buff, obj[i]);
        }
    }

    @Override
    public ByteBuf read(ByteBuffer buff) {
        final int payloadSize = buff.getInt();
        byte[] payload = new byte[payloadSize];
        buff.get(payload);
        return Unpooled.wrappedBuffer(payload);
    }

    @Override
    public void write(WriteBuffer buff, Object obj) {
        final ByteBuf casted = (ByteBuf) obj;
        if(casted.refCnt()>0){
            final int payloadSize = casted.readableBytes();
            byte[] rawBytes = new byte[payloadSize];
            ByteBuf copy = casted.copy();
            copy.readBytes(rawBytes);
            buff.putInt(payloadSize);
            buff.put(rawBytes);
            ReferenceCountUtil.safeRelease(copy);
            ReferenceCountUtil.safeRelease(casted);
        }
    }
}
