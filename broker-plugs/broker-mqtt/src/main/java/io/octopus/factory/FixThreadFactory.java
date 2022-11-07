//package io.octopus.factory;
//
//import io.netty.util.concurrent.DefaultThreadFactory;
//import io.netty.util.concurrent.FastThreadLocalThread;
//
//import java.util.concurrent.atomic.AtomicInteger;
//
//
///**
// * @author chenxu
// * @version 1
// */
//public class FixThreadFactory extends DefaultThreadFactory {
//
//    private final String poolName;
//    private final AtomicInteger nextId = new AtomicInteger(0);
//
//    public FixThreadFactory(String poolName) {
//        super(poolName);
//        this.poolName = poolName;
//    }
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
