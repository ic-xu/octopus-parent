package io.store.util;

import io.octopus.kernel.utils.ByteUtils;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

public class ByteUtilsTest {

    @Test
    public void int2byte() {

        byte[] bytes = ByteUtils.int2byte(356);
        System.out.println(Arrays.toString(bytes));
        int result  = ByteUtils.byteArray2Int(bytes);
        System.out.println(result);
    }

    @Test
    public void byteArray2Int() {
    }
}