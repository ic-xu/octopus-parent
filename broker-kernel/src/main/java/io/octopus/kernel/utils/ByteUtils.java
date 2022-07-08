package io.octopus.kernel.utils;

import io.netty.util.internal.ObjectUtil;

/**
 * @author chenxu
 * @version 1
 * @date $ $
 */
public class ByteUtils {

    //int 转化为字节数组
    public static byte[] int2byte(int num) {
        return new byte[]{(byte) ((num >> 24) & 0xff), (byte) ((num >> 16) & 0xff), (byte) ((num >> 8) & 0xff), (byte) (num & 0xff)};
    }


    //int 转化为字节数组
    public static void int2byte(byte[] bytes, int num, int index) {
        ObjectUtil.checkInRange(index + 4, index, bytes.length, "arr length must index +4");
        bytes[index++] = (byte) ((num >> 24) & 0xff);
        bytes[index++] = (byte) ((num >> 24) & 0xff);
        bytes[index++] = (byte) ((num >> 8) & 0xff);
        bytes[index] = (byte) (num & 0xff);
    }

    //字节数组转化为int
    public static int byteArray2Int(byte[] arr) {
        return (arr[0] & 0xff) << 24 | (arr[1] & 0xff) << 16 | (arr[2] & 0xff) << 8 | (arr[3] & 0xff);
    }

    //字节数组转化为int
    public static int byteArray2Int(byte[] arr, int index) {
        return (arr[index] & 0xff) << 24 | (arr[index + 1] & 0xff) << 16 | (arr[index + 2] & 0xff) << 8 | (arr[index + 3] & 0xff);
    }

    //int 转化为字节数组
    public static void short2byte(byte[] bytes, int num, int index) {
        ObjectUtil.checkInRange(bytes.length, index, index + 4, "arr length must index +4");
        bytes[index++] = (byte) ((num >> 8) & 0xff);
        bytes[index] = (byte) (num & 0xff);
    }

    //字节数组转化为int
    public static int byteArray2Short(byte[] arr, int index) {
        return  ((arr[index] & 0xff) << 8 | (arr[index + 1] & 0xff));
    }


    public static void long2Bytes(byte[] bytes, long num) {
        for (int ix = 0; ix < 8; ++ix) {
            int offset = 64 - (ix + 1) * 8;
            bytes[ix] = (byte) ((num >> offset) & 0xff);
        }
    }

    public static long bytes2Long(byte[] byteNum) {
        long num = 0;
        for (int ix = 0; ix < 8; ++ix) {
            num <<= 8;
            num |= (byteNum[ix] & 0xff);
        }
        return num;
    }

    public static long bytes2Long(byte[] byteNum, int offset) {
        long num = 0;
        for (int ix = 0; ix < 8; ++ix) {
            num <<= 8;
            num |= (byteNum[offset + ix] & 0xff);
        }
        return num;
    }

}
