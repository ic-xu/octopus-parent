package io.octopus.kernel.kernel.queue;

/**
 * @author chenxu
 * @version 1
 */
public class StoreMsg<T> {

    private final T msg;

    private final Index index;

    public StoreMsg(T msg, Index index) {
        this.msg = msg;
        this.index = index;
    }

    public Index getIndex() {
        return index;
    }

    public T getMsg() {
        return msg;
    }

}
