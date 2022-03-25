package io.octopus.base.queue;

/**
 * @author chenxu
 * @version 1
 */
public class StoreMsg<T> {

    private final T msg;

    private final MsgIndex index;

    public StoreMsg(T msg, MsgIndex index) {
        this.msg = msg;
        this.index = index;
    }

    public MsgIndex getIndex() {
        return index;
    }

    public T getMsg() {
        return msg;
    }

}
