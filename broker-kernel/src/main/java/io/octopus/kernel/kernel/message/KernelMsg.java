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
public class KernelMsg implements IMessage, ByteBufHolder {

    private IPackageId idWrapper;

    private MsgQos qos;

    private final MsgRouter msgRouter;

    private final String topic;

    private final ByteBuf payload;

    private final boolean isRetain;


    public KernelMsg(IPackageId idWrapper, MsgRouter msgRouter, String topic, ByteBuf payload, boolean isRetain) {
        this(idWrapper,MsgQos.AT_MOST_ONCE, msgRouter, topic, payload, isRetain);
    }

    public KernelMsg(IPackageId iPackageId, MsgQos qos, MsgRouter msgRouter, String topic, ByteBuf payload, boolean isRetain) {
        this.idWrapper = iPackageId;
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


    @Override
    public Long longId() {
        return idWrapper.longId();
    }


    @Override
    public Short shortId() {
        return idWrapper.shortId();
    }

    /**
     * 是否保留消息
     * @return
     */
    public boolean isRetain() {
        return isRetain;
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof KernelMsg)) {
            return false;
        }
        KernelMsg kernelMsg = (KernelMsg) o;
        return Objects.equals(longId(), kernelMsg.longId());
    }

    @Override
    public int hashCode() {
        return Objects.hash(longId(), getQos(), getMessageType(), getTopic(), getPayload());
    }


    @Override
    public ByteBuf content() {
        return ByteBufUtil.ensureAccessible(payload);
    }

    @Override
    public KernelMsg copy() {
        return replace(content().copy());
    }

    @Override
    public KernelMsg duplicate() {
        return replace(content().duplicate());
    }

    @Override
    public KernelMsg retainedDuplicate() {
        return replace(content().retainedDuplicate());
    }

    @Override
    public KernelMsg replace(ByteBuf content) {
        return new KernelMsg(idWrapper, msgRouter, topic, payload.copy(), isRetain);
    }

    @Override
    public int refCnt() {
        return content().refCnt();
    }

    @Override
    public KernelMsg retain() {
        content().retain();
        return this;
    }

    @Override
    public KernelMsg retain(int increment) {
        content().retain(increment);
        return this;
    }

    @Override
    public KernelMsg touch() {
        content().touch();
        return this;
    }

    @Override
    public KernelMsg touch(Object hint) {
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
