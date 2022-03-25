package io.store;

import io.handler.codec.mqtt.*;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.octopus.base.queue.StoreMsg;
import io.store.persistence.disk.CheckPointServer;
import io.store.persistence.disk.ConcurrentFileQueue;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicInteger;

public class ConcurrentFileQueueTest {
    ConcurrentFileQueue concurrentFileQueue;

    @Before
    public void before() throws IOException {
        concurrentFileQueue = new ConcurrentFileQueue(null,0,new CheckPointServer());
//        MqttPublishMessage mqttPublishMessage = publishNotRetainedDuplicated(1, "test/bb/cc" + 1, MqttQoS.AT_LEAST_ONCE, Unpooled.wrappedBuffer(("hhhh-ggg-ddd" + 1).getBytes(StandardCharsets.UTF_8)));
//        concurrentFileQueue.offer(mqttPublishMessage);
    }

    @Test
    public void mapBuffer() throws IOException {

        RandomAccessFile randomAccessFile = new RandomAccessFile("a.txt", "rw");
        MappedByteBuffer mappedByteBuffer = randomAccessFile.getChannel().map(FileChannel.MapMode.READ_WRITE, 0, 100);
        byte[] bytes = "abcdefg===".getBytes(StandardCharsets.UTF_8);
        byte[] read = new byte[10];
        mappedByteBuffer.put(bytes, 0, 5);
        mappedByteBuffer.flip();
        mappedByteBuffer.get(read, 3, 2);

        System.out.println(Arrays.toString(read));
//        mappedByteBuffer.put(bytes);
    }

    @Test
    public void offer() throws IOException {

        String testMessage = "ff";
        for (int i = 0; i < 100; i++) {
            testMessage = testMessage + i;
        }

        long startTime = System.currentTimeMillis();
        for (int i = 2; i < 100000; i++) {
            MqttPublishMessage mqttPublishMessage = publishNotRetainedDuplicated(i % 65535 + 1, "test/bb/cc" + i, MqttQoS.AT_LEAST_ONCE, Unpooled.wrappedBuffer(("ff" + i).getBytes(StandardCharsets.UTF_8)));
            concurrentFileQueue.offer(mqttPublishMessage);
        }

        for (int i = 2; i < 120000; i++) {
            StoreMsg<IMessage> poll =  concurrentFileQueue.poll();
            if (null != poll) {
                System.out.println(((MqttPublishMessage)poll.getMsg()).variableHeader().topicName());
            }

        }
//
//        System.out.println("-------------------------------------");
//        MqttPublishMessage mqttPublishMessage = publishNotRetainedDuplicated(23, "test/bb/cc" + 1000001, MqttQoS.AT_LEAST_ONCE, Unpooled.wrappedBuffer((testMessage).getBytes(StandardCharsets.UTF_8)));
//        concurrentFileQueue.offer(mqttPublishMessage);
//
//
//        MqttPublishMessage poll = (MqttPublishMessage) concurrentFileQueue.poll();
//        if (null != poll) {
//            System.out.println(poll.variableHeader().topicName());
//        }
        System.err.println(System.currentTimeMillis() - startTime);

    }

    private MqttPublishMessage publishNotRetainedDuplicated(int packetId, String topic, MqttQoS qos, ByteBuf payload) {
        MqttFixedHeader fixedHeader = new MqttFixedHeader(MqttMessageType.PUBLISH, true, qos, false, 0);
        MqttPublishVariableHeader varHeader = new MqttPublishVariableHeader(topic.toString(), packetId);
        return new MqttPublishMessage(fixedHeader, varHeader, payload);
    }

    @Test
    public void poll() throws IOException {

        AtomicInteger ff = new AtomicInteger(0);
        System.out.println(ff.getAndAdd(1));
        System.out.println(ff.getAndAdd(1));
        System.out.println(ff.addAndGet(1));
    }

    @Test
    public void ff() {
//        byte[] bytes = FileMappedByteBuffer.toLH(30);
//        System.out.println(FileMappedByteBuffer.toInt(bytes));
    }

    @Test
    public void linkHashSet() {
        Set<Integer> integers = new TreeSet<>();
        integers.add(2);
        integers.add(4);
        integers.add(3);

        integers.forEach(System.out::println);
    }

    @Test
    public void messageSize(){

        System.out.println((Long.MAX_VALUE ));
        System.out.println((Long.MAX_VALUE / 1024 / 1024 / 1024)+" GB");
        System.out.println((Long.MAX_VALUE / 1024 / 1024 / 1024/1024)+" TB");
        System.out.println((Long.MAX_VALUE / 1024 / 1024 / 1024/1024/1024)+" ZB");
    }


    public static void main(String[] args) throws IOException {
        File dir = new File("data" + File.separator + 0);
        if (!dir.getParentFile().exists()) {
            dir.getParentFile().mkdirs();
        }
        RandomAccessFile file = new RandomAccessFile(dir, "rw");
        MappedByteBuffer map = file.getChannel().map(FileChannel.MapMode.READ_WRITE, 0, 1024 * 1024 * 1024);
        AtomicInteger position = new AtomicInteger(0);
//        byte[] length = new byte[4];
//        for (int i = 0; i <length.length ; i++) {
//            int andAdd = position.getAndAdd(1);
//           length[i] =map.get(andAdd);
//        }
//        System.out.println(FileMappedByteBuffer.toInt(length));

//        System.out.println(map.get(0));
//        System.out.println(map.get(1));
//        System.out.println(map.get(2));
//        System.out.println(map.get(3));

        System.out.println(map.getInt(0));
//        int anInt = map.getInt();
//        while (anInt>0){
//            System.out.println(anInt);
//            byte[] bytes = new byte[anInt];
//            ByteBuffer byteBuffer = map.get(bytes);
//            System.out.println(new String(bytes,StandardCharsets.UTF_8));
//            anInt = map.getInt();
//        }

//
//        int anInt1 = map.getInt();
//        System.out.println(anInt1);
//        System.out.println();
    }

}