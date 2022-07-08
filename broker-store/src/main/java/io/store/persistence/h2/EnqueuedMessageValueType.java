package io.store.persistence.h2;


import io.netty.buffer.ByteBuf;
import io.octopus.kernel.kernel.message.MsgQos;
import io.octopus.kernel.kernel.message.PubRelMarker;
import io.octopus.kernel.kernel.message.PublishedMessage;
import io.octopus.kernel.kernel.subscriptions.Topic;
import org.h2.mvstore.WriteBuffer;
import org.h2.mvstore.type.StringDataType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;

public final class EnqueuedMessageValueType implements org.h2.mvstore.type.DataType {

    Logger logger = LoggerFactory.getLogger(EnqueuedMessageValueType.class);

    private enum MessageType {PUB_REL_MARKER, PUBLISHED_MESSAGE}

    private final StringDataType topicDataType = new StringDataType();
    private final ByteBufDataType payloadDataType = new ByteBufDataType();

    @Override
    public int compare(Object a, Object b) {
        return 0;
    }

    @Override
    public int getMemory(Object obj) {
        if (obj instanceof PubRelMarker) {
            return 3;
        }
        final PublishedMessage casted = (PublishedMessage) obj;
        return 1 + // message type
                1 + // qos
                2 + //packageId
                topicDataType.getMemory(casted.getTopic().toString()) +
                payloadDataType.getMemory(casted.getPayload());
    }

    @Override
    public void write(WriteBuffer buff, Object obj) {
        if (obj instanceof PublishedMessage) {
            final PublishedMessage casted = (PublishedMessage) obj;
            try {
                ByteBuf payload = casted.getPayload().copy();

                buff.put((byte) MessageType.PUBLISHED_MESSAGE.ordinal());
                buff.putShort((short) casted.getPackageId());

                buff.put((byte) casted.getPublishingQos().getValue());
                final String token = casted.getTopic().toString();
                topicDataType.write(buff, token);
                payloadDataType.write(buff, payload);
            } catch (Exception e) {
                logger.error(e.getMessage());
            }
        } else if (obj instanceof PubRelMarker) {
            buff.put((byte) MessageType.PUB_REL_MARKER.ordinal());
            buff.putShort((short) ((PubRelMarker) obj).getPackageId());
        } else {
            throw new IllegalArgumentException("Unrecognized message class " + obj.getClass());
        }
    }

    @Override
    public void write(WriteBuffer buff, Object[] obj, int len, boolean key) {
        for (int i = 0; i < len; i++) {
            write(buff, obj[i]);
        }
    }

    @Override
    public Object read(ByteBuffer buff) {
        final byte messageType = buff.get();
        final short messageId = buff.getShort();
        if (messageType == MessageType.PUB_REL_MARKER.ordinal()) {
            return new PubRelMarker(messageId);
        } else if (messageType == MessageType.PUBLISHED_MESSAGE.ordinal()) {
            final MsgQos qos = MsgQos.valueOf(buff.get());
            final String topicStr = topicDataType.read(buff);
            final ByteBuf payload = payloadDataType.read(buff);
            return new PublishedMessage(messageId, Topic.asTopic(topicStr), qos, payload);
        } else {
            throw new IllegalArgumentException("Can't recognize record of type: " + messageType);
        }
    }

    @Override
    public void read(ByteBuffer buff, Object[] obj, int len, boolean key) {
        for (int i = 0; i < len; i++) {
            obj[i] = read(buff);
        }
    }
}
