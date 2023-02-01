package io.octopus.kernel.kernel.message;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufHolder;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.octopus.kernel.exception.NoSuchObjectException;
import io.octopus.kernel.utils.ObjectUtils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Properties;

/**
 * @author chenxu
 * @version 1
 * @date 2022/1/5 8:14 下午
 */
public class KernelPayloadMessage extends KernelMessage implements ByteBufHolder {

    private MsgQos qos;

    private final MsgRouter msgRouter;

    private final String topic;

    private final ByteBuf payload;

    private final boolean retain;

    private final Properties properties = new Properties();


    @Override
    public Integer getSize() throws IOException {
        byte[] topicBytes = topic.getBytes(StandardCharsets.UTF_8);
        return super.getSize() + 1 + 1 + 1 + topicBytes.length + payload.capacity() + getPropertiesByteArr().length;
    }


    private byte[] getPropertiesByteArr() throws IOException {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        properties.store(byteArrayOutputStream, "properties");
        return byteArrayOutputStream.toByteArray();
    }

    @Override
    public synchronized byte[] toByteArr() throws IOException {
        ///qos -> 1byte，msgRouter -> 1byte, isRetain->1byte
        ByteBuf byteBuffer = Unpooled.buffer();
        //packageId + packagePubEnum
        byteBuffer.writeBytes(super.toByteArr());
        //qos -> 1byte
        byteBuffer.writeByte(qos.getValue());
        //msgRouter -> 1byte,
        byteBuffer.writeByte(msgRouter.getValue());
        //isRetain->1byte
        byteBuffer.writeByte((byte) (retain ? 1 : 0));
        //topic
        byte[] bytes = topic.getBytes(StandardCharsets.UTF_8);
        byteBuffer.writeInt(bytes.length);
        byteBuffer.writeBytes(bytes);

        /// properties
        byte[] propertiesByteArr = getPropertiesByteArr();
        byteBuffer.writeInt(propertiesByteArr.length);
        byteBuffer.writeBytes(propertiesByteArr);
        //payload
        byteBuffer.writeBytes(payload);
        return ByteBufUtil.getBytes(payload);
    }


    public static KernelPayloadMessage fromByteArr(byte[] byteArr) throws NoSuchObjectException, IOException {
        ByteBuf byteBuf = Unpooled.wrappedBuffer(byteArr);
        short packageId = byteBuf.readShort();
        long longPackageId = byteBuf.readLong();
        PubEnum pubEnum1 = PubEnum.valueOf(byteBuf.readByte());
        MsgQos qos = MsgQos.valueOf(byteBuf.readByte());
        MsgRouter router = MsgRouter.valueOf(byteBuf.readByte());
        boolean retain = byteBuf.readByte() > 0;
        int topicLength = byteBuf.readInt();

        //topic String
        byte[] bytes = new byte[topicLength];
        byteBuf.readBytes(bytes);
        String topic = new String(bytes,StandardCharsets.UTF_8);
        /// properties
        int propertiesLength = byteBuf.readInt();
        byte[] bytesProperties = new byte[propertiesLength];
        byteBuf.readBytes(bytesProperties);
        Properties properties = new Properties();
        properties.load(new ByteArrayInputStream(bytesProperties));


        /// payload
        ByteBuf payload = byteBuf;

        return new KernelPayloadMessage(packageId,longPackageId,properties,qos,router,topic,payload,retain,pubEnum1);
    }

    public KernelPayloadMessage(short packageId, MsgRouter msgRouter, String topic, ByteBuf payload, boolean isRetain) {
        this(packageId,0L, null, MsgQos.AT_MOST_ONCE, msgRouter, topic, payload, isRetain, PubEnum.PUBLISH);
    }

    public KernelPayloadMessage(short packageId, Properties properties, MsgRouter msgRouter, String topic, ByteBuf payload, boolean isRetain) {
        this(packageId, 0L,properties, MsgQos.AT_MOST_ONCE, msgRouter, topic, payload, isRetain, PubEnum.PUBLISH);
    }

    public KernelPayloadMessage(short packageId,Long longPackageId, Properties properties, MsgQos qos, MsgRouter msgRouter, String topic, ByteBuf payload, boolean retain, PubEnum pubEnum) {
        super(packageId, pubEnum,longPackageId);
        this.qos = qos;
        this.msgRouter = msgRouter;
        this.topic = topic;
        this.payload = payload;
        this.retain = retain;

        if (!ObjectUtils.isEmpty(properties)) {
            this.properties.putAll(properties);
        }
    }

    public KernelPayloadMessage(short packageId, MsgQos qos, MsgRouter msgRouter, String topic, ByteBuf payload, boolean retain, PubEnum pubEnum) {
        super(packageId, pubEnum);
        this.qos = qos;
        this.msgRouter = msgRouter;
        this.topic = topic;
        this.payload = payload;
        this.retain = retain;
    }

    public MsgQos getQos() {
        return qos;
    }


    public void setQos(MsgQos qos) {
        this.qos = qos;
    }

    public MsgRouter getMessageType() {
        return msgRouter;
    }


    public String getTopic() {
        return topic;
    }


    public ByteBuf getPayload() {
        return payload;
    }


    /**
     * 是否保留消息
     *
     * @return retain | boolean
     */
    public boolean isRetain() {
        return retain;
    }


    public Properties getProperties() {
        return properties;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof KernelPayloadMessage kernelPayloadMessage)) {
            return false;
        }
        return Objects.equals(packageId(), kernelPayloadMessage.packageId())
                && Objects.equals(kernelPayloadMessage.getPubEnum(), this.getPubEnum())
                && Objects.equals(kernelPayloadMessage.qos, this.qos)
                && Objects.equals(kernelPayloadMessage.payload, this.payload)
                && Objects.equals(kernelPayloadMessage.topic, this.topic);
    }

    @Override
    public int hashCode() {
        return Objects.hash(packageId(), getQos(), getMessageType(), getTopic(), getPayload());
    }


    @Override
    public ByteBuf content() {
        return ByteBufUtil.ensureAccessible(payload);
    }

    @Override
    public KernelPayloadMessage copy() {
        return replace(content().copy());
    }

    @Override
    public KernelPayloadMessage duplicate() {
        return replace(content().duplicate());
    }

    @Override
    public KernelPayloadMessage retainedDuplicate() {
        return replace(content().retainedDuplicate());
    }

    @Override
    public KernelPayloadMessage replace(ByteBuf content) {
        return new KernelPayloadMessage(packageId(), properties, msgRouter, topic, payload.copy(), retain);
    }

    @Override
    public int refCnt() {
        return content().refCnt();
    }

    @Override
    public KernelPayloadMessage retain() {
        content().retain();
        return this;
    }

    @Override
    public KernelPayloadMessage retain(int increment) {
        content().retain(increment);
        return this;
    }

    @Override
    public KernelPayloadMessage touch() {
        content().touch();
        return this;
    }

    @Override
    public KernelPayloadMessage touch(Object hint) {
        content().touch(hint);
        return this;
    }

    @Override
    public boolean release() {
        return content().release();
    }

    @Override
    public boolean release(int decrement) {
        return content().release(decrement);
    }


}
