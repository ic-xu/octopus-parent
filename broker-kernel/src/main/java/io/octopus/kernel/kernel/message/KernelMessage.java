package io.octopus.kernel.kernel.message;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Objects;

/**
 * @author chenxu
 * @version 1
 * @date 2022/1/5 8:14 下午
 */
public class KernelMessage implements IMessage {

    private Short packageId;

    private final PubEnum pubEnum;

    private final Long messageId;

    public KernelMessage(short packageId, PubEnum pubEnum) {
        this(packageId,pubEnum,0L);
    }

    public KernelMessage(short packageId, PubEnum pubEnum,Long messageId) {
        this.packageId = packageId;
        this.pubEnum = pubEnum;
        this.messageId = messageId;
    }


    public Integer getSize() throws IOException {
        return 11;
    }

    @Override
    public byte[] toByteArr() throws IOException {
        ByteBuffer byteBuffer = ByteBuffer.allocate(getSize());
        byteBuffer.putShort(packageId);
        byteBuffer.putLong(messageId);
        byteBuffer.put(pubEnum.getValue());
        return byteBuffer.array();
    }

    @Override
    public MsgQos getQos() {
        return MsgQos.AT_MOST_ONCE;
    }


    public static KernelMessage fromByte(byte[] byteArr) {
        ByteBuf byteBuf = Unpooled.wrappedBuffer(byteArr);
        short packageId = byteBuf.readShort();
        long longPackageId = byteBuf.readLong();
        byte b = byteBuf.readByte();
        PubEnum pubEnum1 = PubEnum.valueOf(b);
        return new KernelMessage(packageId, pubEnum1);
    }


    public PubEnum getPubEnum() {
        return pubEnum;
    }


    public void setPackageId(short packageId) {
        this.packageId = packageId;
    }

    @Override
    public Long messageId() {
        return messageId;
    }

    public Short packageId() {
        return packageId;
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof KernelMessage kernelMsg)) {
            return false;
        }
        return Objects.equals(packageId(), kernelMsg.packageId())
                && Objects.equals(kernelMsg.pubEnum, this.pubEnum);
    }

    @Override
    public int hashCode() {
        return Objects.hash(packageId());
    }


}
