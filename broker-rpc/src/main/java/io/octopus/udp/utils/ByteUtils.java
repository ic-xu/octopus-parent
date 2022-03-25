package io.octopus.udp.utils;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ByteUtils {
    public static List<byte[]> copy(byte[] bytes, int capacity) {
        ArrayList<byte[]> bytesArray = new ArrayList<>();
        int sum = getSegmentSize(bytes.length,capacity);
        if (sum == 1) {
            bytesArray.add(bytes);
            return bytesArray;
        }
        for (int i = 0; i < sum; i++) {
            byte[] bytesTmp;
            if (i == sum - 1) {
                bytesTmp = Arrays.copyOfRange(bytes, i * capacity, bytes.length);
            } else {
                bytesTmp = Arrays.copyOfRange(bytes, i * capacity, (i + 1) * capacity);
            }
            bytesArray.add(bytesTmp);
        }
        return bytesArray;
    }



    public static Integer getSegmentSize(Integer length,int capacity){
        int sum = length / capacity;
        if (length % capacity != 0) {
            sum += 1;
        }
        return sum;
    }

    public static int getInt(byte[] bb, int index) {
        return ((((bb[index] & 0xff) << 24)
                | ((bb[index + 1] & 0xff) << 16)
                | ((bb[index + 2] & 0xff) << 8)
                | ((bb[index + 3] & 0xff))));
    }

    public static short getShort(byte[] b, int index) {
        return (short) (((b[index] << 8) | b[index + 1] & 0xff));
    }


    public static long getLong(byte[] bb, int index) {
        return ((((long) bb[index] & 0xff) << 56)
                | (((long) bb[index + 1] & 0xff) << 48)
                | (((long) bb[index + 2] & 0xff) << 40)
                | (((long) bb[index + 3] & 0xff) << 32)
                | (((long) bb[index + 4] & 0xff) << 24)
                | (((long) bb[index + 5] & 0xff) << 16)
                | (((long) bb[index + 6] & 0xff) << 8)
                | (((long) bb[index + 7] & 0xff)));
    }

    public static String getString(byte[] bb, int index, int length) {
        return new String(bb, index, length, StandardCharsets.UTF_8);
    }

    public static String getString(byte[] bb) {
        return new String(bb, StandardCharsets.UTF_8);
    }
}
