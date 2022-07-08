package io.octopus.kernel.kernel.message;

import io.octopus.kernel.exception.NoSuchObjectException;

/**
 * @author chenxu
 * @version 1
 * @date 2022/1/5 8:19 下午
 */
public enum MsgRouter {

    /**
     * 消息路由类型，P2P
     */
    USER_NAME(1),TOPIC(2);

    private final int value;

    MsgRouter(int value){
        this.value = value;
    }

    public int getValue() {
        return value;
    }


    public static MsgRouter valueOf(int value) throws NoSuchObjectException {
        switch (value){
            case 1:return USER_NAME;
            case 2:return TOPIC;
            default:throw new NoSuchObjectException();
        }
    }

}
