package io.octopus.utils;

import io.netty.buffer.ByteBuf;
import io.netty.util.ReferenceCountUtil;

import java.nio.charset.StandardCharsets;

public final class DebugUtils {

    public static String payload2Str(ByteBuf content) {
        final ByteBuf copy = content.copy();
        try {
            final byte[] bytesContent;
            if (copy.isDirect()) {
                final int size = copy.readableBytes();
                bytesContent = new byte[size];
                copy.readBytes(bytesContent);
            } else {
                bytesContent = copy.array();
            }
            return new String(bytesContent, StandardCharsets.UTF_8);
        }finally {
            ReferenceCountUtil.safeRelease(copy);
        }
    }
}
