//package io.octopus.factory;
//
//import io.netty.util.concurrent.FastThreadLocal;
//import io.netty.util.internal.ObjectUtil;
//
///**
// * @author chenxu
// * @version 1
// */
//final class FastThreadLocalRunnable implements Runnable {
//    private final Runnable runnable;
//
//    private FastThreadLocalRunnable(Runnable runnable) {
//        this.runnable = (Runnable) ObjectUtil.checkNotNull(runnable, "runnable");
//    }
//
//    public void run() {
//        try {
//            this.runnable.run();
//        } finally {
//            FastThreadLocal.removeAll();
//        }
//
//    }
//
//    static Runnable wrap(Runnable runnable) {
//        return (Runnable)(runnable instanceof FastThreadLocalRunnable ? runnable : new FastThreadLocalRunnable(runnable));
//    }
//}