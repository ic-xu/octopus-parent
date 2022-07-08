package io.octopus.kernel.utils;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

public class HostUtilsTest {

    @Test
    @RepeatedTest(100)
    public void getOneIpv4Address() {
        Assertions.assertNotEquals(HostUtils.getAnyIpv4Address(), null);
        System.out.println(HostUtils.getAnyIpv4Address());
    }
}