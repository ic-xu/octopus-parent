package io.store.persistence.disk;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.BitSet;

import static org.junit.Assert.*;

public class IndexFileTest {

    private BitSet bitSet = new BitSet(1024 * 1024);

    @Before
    public void setUp() throws Exception {
    }

    @After
    public void tearDown() throws Exception {
    }

    @Test
    public void test1() {
        System.out.println(Integer.MAX_VALUE);
    }
}