package io.octopus.kernel.utils;

import org.junit.jupiter.api.Test;

public class ByteUtilsTest {

    @Test
    public void int2byte() {
        int[] ints = new int[10];

        int j = 0;
        ints[j++] = 1;
        ints[j++] = 2;
        ints[j++] = 3;
        ints[j++] = 4;


        System.out.println(ints[0]);
        System.out.println(ints[3]);
        System.out.println(ints[4]);

    }
}