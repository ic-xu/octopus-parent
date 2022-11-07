//package io.octopus.factory;
//
//import io.netty.util.concurrent.FastThreadLocalThread;
//
//import java.util.concurrent.ThreadFactory;
//import java.util.concurrent.atomic.AtomicInteger;
//
//
///**
// * @author chenxu
// * @version 1
// */
//public class PublishThreadFactory implements ThreadFactory {
//
//    private final String poolName;
//    private final AtomicInteger nextId = new AtomicInteger(0);
//    private final ThreadGroup threadGroup;
//
//
//    public PublishThreadFactory(String poolName, ThreadGroup threadGroup) {
//        this.poolName = poolName;
//        this.threadGroup = threadGroup;
//    }
//
//
//    @Override
//    public Thread newThread(Runnable r) {
//        Thread t = this.newThread(FastThreadLocalRunnable.wrap(r), this.poolName +"-"+ this.nextId.incrementAndGet());
//        t.setDaemon(false);
//        t.setPriority(5);
//        return t;
//    }
//
//    protected Thread newThread(Runnable r, String name) {
//        return new FastThreadLocalThread(this.threadGroup, r, name);
//    }
//}
