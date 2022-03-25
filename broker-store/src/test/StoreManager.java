//package io.store;
//
//import io.handler.codec.mqtt.MqttMessage;
//import java.io.IOException;
//
//public class StoreManager {
//
//
//   private final static InheritableThreadLocal<ConcurrentFileQueue> localQueue = new InheritableThreadLocal<>();
//
////    private final ExecutorService writeDiskService = Executors.newFixedThreadPool(1, new DefaultThreadFactory("writeDiskService"));
////    private final ExecutorService flushDiskService = Executors.newFixedThreadPool(1, new DefaultThreadFactory("flushDiskService"));
//
//
//    public ConcurrentFileQueue getConcurrentFileQueue() throws IOException {
//        ConcurrentFileQueue concurrentFileQueue = localQueue.get();
//        if(null == concurrentFileQueue){
//            concurrentFileQueue = new ConcurrentFileQueue(Thread.currentThread().getName());
//        }
//        return concurrentFileQueue;
//    }
//
//    public boolean offer(MqttMessage msg) throws IOException {
//        ConcurrentFileQueue concurrentFileQueue = localQueue.get();
//        if(null==concurrentFileQueue){
//            String threadName = Thread.currentThread().getName();
//            concurrentFileQueue = new ConcurrentFileQueue(threadName);
//            localQueue.set(concurrentFileQueue);
//        }
//       return concurrentFileQueue.offer(msg);
//    }
//
//
////    class WriteDiskWorker implements Runnable {
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
////    class FlushDiskWorker implements  Runnable{
////
////        @Override
////        public void run() {
////            concurrentFileQueue.flush();
////        }
////    }
//
//}
