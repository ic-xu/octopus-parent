//package io.octopus.persistence;
//
//import io.handler.codec.mqtt.Message;
//import io.octopus.broker.MsgDispatcher;
//import io.octopus.broker.session.PullMessageWorker;
//import io.octopus.base.checkpoint.CheckPoint;
//import io.octopus.factory.PublishThreadFactory;
//import io.octopus.base.queue.MsgQueue;
//import io.octopus.base.queue.StoreMsgResult;
//import io.store.persistence.disk.CheckPointServer;
//import io.store.persistence.disk.ConcurrentFileQueue;
//
//import java.io.IOException;
//import java.util.Map;
//import java.util.concurrent.ConcurrentHashMap;
//import java.util.concurrent.LinkedBlockingQueue;
//import java.util.concurrent.ThreadPoolExecutor;
//import java.util.concurrent.TimeUnit;
//import java.util.concurrent.atomic.AtomicBoolean;
//
//public class ThreadQueue implements MsgQueue<Message> {
//
//    private final InheritableThreadLocal<ConcurrentFileQueue> localQueue = new InheritableThreadLocal<>();
//
//    //    private final ExecutorService writeDiskService = Executors.newFixedThreadPool(1, new DefaultThreadFactory("writeDiskService"));
////    private final ScheduledExecutorService flushDiskService = Executors.newScheduledThreadPool(1, new DefaultThreadFactory("flushDiskService"));
//
//    private final static Map<String, ThreadPoolExecutor> CHILD_THREAD_MAP = new ConcurrentHashMap<>();
//    private final static Map<String, ConcurrentFileQueue> QUEUE_MAP = new ConcurrentHashMap<>();
//
//    private MsgDispatcher dispatcher;
//
//    private final AtomicBoolean stopFlag = new AtomicBoolean(false);
//
//    public ThreadQueue() {
////        flushDiskService.scheduleWithFixedDelay(new FlushDiskWorker(), 1, 1, TimeUnit.SECONDS);
//
//    }
//
//    @Override
//    public void initialize(Object params){
//        this.dispatcher = (MsgDispatcher) params;
//    }
//
//
//    public ConcurrentFileQueue getConcurrentFileQueue() throws IOException {
//        ConcurrentFileQueue concurrentFileQueue = localQueue.get();
//        if (null == concurrentFileQueue) {
//            throw new NullPointerException("concurrentFileQueue is null");
//        }
//        return concurrentFileQueue;
//    }
//
//
//    @Override
//
//    public StoreMsgResult<Message> offer(Message msg) {
//        ConcurrentFileQueue concurrentFileQueue = localQueue.get();
//        if (null == concurrentFileQueue) {
//            String threadName = Thread.currentThread().getName();
//            try {
//                concurrentFileQueue = new ConcurrentFileQueue(threadName, new CheckPointServer());
//            } catch (IOException e) {
//                e.printStackTrace();
//                return null;
//            }
//            QUEUE_MAP.put(threadName, concurrentFileQueue);
//            localQueue.set(concurrentFileQueue);
//            ThreadPoolExecutor singleThreadExecutor = CHILD_THREAD_MAP.get(threadName);
//            if (null == singleThreadExecutor) {
//                singleThreadExecutor = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.SECONDS,
//                        new LinkedBlockingQueue<>(1),
//                        new PublishThreadFactory(threadName + "-publish", Thread.currentThread().getThreadGroup()),
//                        new ThreadPoolExecutor.DiscardPolicy());
//                CHILD_THREAD_MAP.put(threadName, singleThreadExecutor);
//            }
//            singleThreadExecutor.execute(new PullMessageWorker(dispatcher, concurrentFileQueue,stopFlag));
//        }
//        return concurrentFileQueue.offer(msg);
//    }
//
//
//    @Override
//    public StoreMsgResult<Message> poll() {
//        return null;
//    }
//
//    @Override
//    public int size() {
//        try {
//            return getConcurrentFileQueue().size();
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//        return 0;
//    }
//
//    @Override
//    public Message poll(long offset) {
//        try {
//            return getConcurrentFileQueue().poll(offset);
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//        return null;
//    }
//
//    @Override
//    public void flushDisk() {
//        try {
//            getConcurrentFileQueue().flushDisk();
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//    }
//
//    @Override
//    public CheckPoint wrapperCheckPoint() {
//        try {
//            return getConcurrentFileQueue().wrapperCheckPoint();
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//        return null;
//    }
//
//    @Override
//    public void stop() {
//        MsgQueue.super.stop();
//        stopFlag.set(true);
//    }
//
//
//    //    class WriteDiskWorker implements Runnable {
////        private StoreMsg msg;
////
////        public WriteDiskWorker(StoreMsg msg) {
////            this.msg = msg;
////        }
////
////        @Override
////        public void run() {
////            assert msg != null;
////            if (concurrentFileQueue.offer(msg.getMsg())) {
////                msg.getListener().flushDiskCall(true,msg.getMsg());
////            }else {
////                msg.getListener().flushDiskCall(false,msg.getMsg());
////            }
////            msg = null;
////        }
////    }
////
////    static class FlushDiskWorker implements Runnable {
////
////        @Override
////        public void run() {
////            queueMap.forEach((key, queue) -> queue.flush());
////        }
////    }
//
//}
