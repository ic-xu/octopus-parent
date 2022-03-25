package client.sync;

import io.netty.channel.Channel;
import io.netty.channel.ChannelException;

import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class SyncRequest<T, I> implements Callable<I> {

    private T body;
    private short id;
    private I response;
    private long timeout;

    private volatile Boolean sendStatus;
    private volatile Throwable cause;
    private Channel remoteChannel;
    private CountDownLatch countDownLatch = new CountDownLatch(1);

    public SyncRequest(T body, short id, long timeout, Channel remoteChannel) {
        this.body = body;
        this.id = id;
        this.timeout = timeout;
        this.remoteChannel = remoteChannel;
    }

    public Boolean getSendStatus() {
        return sendStatus;
    }


    public T getBody() {
        return body;
    }


    public Throwable getCause() {
        return cause;
    }

    /**
     * @param response  ss
     */
    public void putResponse(I response) {
        this.response = response;
    }

    public void wakeUp() {
        countDownLatch.countDown();
    }

    @Override
    public I call() throws Exception {
        SyncRequestManager.INSTANCE.addRequest(id, this);
        if (null == remoteChannel || !remoteChannel.isWritable()) {
            throw new ChannelException("the channel can`t write " + remoteChannel);
        }
        remoteChannel.writeAndFlush(getBody()).addListener(future -> {
            if (future.isSuccess()) {
                this.sendStatus = true;
                return;
            } else {
                this.sendStatus = false;
                this.cause = future.cause();
                this.putResponse(null);
            }
        });
        if (timeout != 0)
            countDownLatch.await(timeout, TimeUnit.MILLISECONDS);
        else countDownLatch.await();
        SyncRequestManager.INSTANCE.removeRequest(id);
        if (null == this.response) {
            throw new TimeoutException("send request time out , ip is " + remoteChannel.remoteAddress());
        }
        return this.response;
    }
}
