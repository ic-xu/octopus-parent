package io.store.persistence.disk;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.BitSet;

public class IndexFileTest {

    private BitSet bitSet = new BitSet(1024 * 1024);

    @BeforeEach
    public void setUp() throws Exception {
    }

    @AfterEach
    public void tearDown() throws Exception {
    }

    @Test
    public void test1() {
        System.out.println(Integer.MAX_VALUE);
    }
}