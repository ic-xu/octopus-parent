//package io.octopus.broker.session;
//
//import io.octopus.base.message.IMessage;
//import io.netty.util.ReferenceCountUtil;
//import io.netty.util.internal.ObjectUtil;
//import io.octopus.broker.MsgDispatcher;
//import io.octopus.base.queue.MsgQueue;
//import io.octopus.base.queue.SearchData;
//import io.octopus.base.queue.StoreMsg;
//import io.octopus.base.utils.ObjectUtils;
//
//import java.util.Queue;
//import java.util.concurrent.atomic.AtomicBoolean;
//
//public class PullMessageWorker implements Runnable {
//    private final MsgDispatcher msgDispatcher;
//    private final MsgQueue<IMessage> messageQueue;
//    private final Queue<SearchData> indexQueue;
//    private final AtomicBoolean stopFlag;
//
//    public PullMessageWorker(MsgDispatcher msgDispatcher, MsgQueue<IMessage> messageQueue, Queue<SearchData> indexQueue, AtomicBoolean stopFlag) {
//        this.msgDispatcher = msgDispatcher;
//        ObjectUtil.checkNotNull(messageQueue, "messageQueue must not null");
//        ObjectUtil.checkNotNull(indexQueue, "indexQueue must not null");
//        this.messageQueue = messageQueue;
//        this.indexQueue = indexQueue;
//        this.stopFlag = stopFlag;
//    }
//
//
//    @Override
//    public void run() {
//        for ( ; ; ) {
//            if (stopFlag.get()) {
//                return;
//            }
//
//            try {
//                SearchData searchData = indexQueue.poll();
//                if (ObjectUtils.isEmpty(searchData)) {
//                    StoreMsg<IMessage> message = messageQueue.poll();
//                    if (ObjectUtils.isEmpty(message)) {
//                        Thread.sleep(0);
//                        continue;
//                    }
//                    msgDispatcher.publishMessage(message);
//                    ReferenceCountUtil.safeRelease(message.getMsg());
//                } else {
//                    StoreMsg<IMessage> message = messageQueue.poll(searchData);
//                    msgDispatcher.publishMessage2ClientId(message, searchData.getClientId());
//                    ReferenceCountUtil.safeRelease(message.getMsg());
//                }
//            } catch (Exception e) {
//                e.printStackTrace();
//            }
//        }
//
//    }
//
//}
