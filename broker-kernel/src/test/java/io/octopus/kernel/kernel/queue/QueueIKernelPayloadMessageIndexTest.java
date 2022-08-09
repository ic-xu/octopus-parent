package io.octopus.kernel.kernel.queue;

import org.junit.jupiter.api.Test;

public class QueueIKernelPayloadMessageIndexTest {

    @Test
    public void getSize() {
    }

    @Test
    public void getOffset() {
    }

    @Test
    public void getQueueName() {
    }

    @Test
    public void fromBytes() {
    }

    @Test
    public void toBytes() {
        Index test001asdfas = new Index(10L, 3643,  65534);
        System.out.println(test001asdfas);
        byte[] bytes = test001asdfas.toBytes();
        Index msgIndex = Index.fromBytes(bytes);
        System.out.println(msgIndex);
    }
}