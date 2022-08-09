package io.octopus.kernel.kernel.message;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufHolder;
import io.netty.buffer.ByteBufUtil;

import java.util.Objects;

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

    private final boolean isRetain;



    public KernelPayloadMessage(short packageId, MsgRouter msgRouter, String topic, ByteBuf payload, boolean isRetain) {
        this(packageId, MsgQos.AT_MOST_ONCE, msgRouter, topic, payload, isRetain, PubEnum.PUBLISH);
    }

    public KernelPayloadMessage(short packageId, MsgQos qos, MsgRouter msgRouter, String topic, ByteBuf payload, boolean isRetain, PubEnum pubEnum) {
       super(packageId,pubEnum);
        this.qos = qos;
        this.msgRouter = msgRouter;
        this.topic = topic;
        this.payload = payload;
        this.isRetain = isRetain;
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
        return isRetain;
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
                && Objects.equals(kernelPayloadMessage.getPubEnum(),this.getPubEnum())
                && Objects.equals(kernelPayloadMessage.qos,this.qos)
                && Objects.equals(kernelPayloadMessage.payload,this.payload)
                && Objects.equals(kernelPayloadMessage.topic,this.topic);
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
        return new KernelPayloadMessage(packageId(), msgRouter, topic, payload.copy(), isRetain);
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
