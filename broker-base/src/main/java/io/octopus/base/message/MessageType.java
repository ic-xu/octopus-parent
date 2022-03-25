package io.octopus.base.message;

/**
 * @author chenxu
 * @version 1
 * @date 2022/1/5 8:19 下午
 */
public enum MessageType {

    STRING(1);

    private final int value;

    MessageType(int value){
        this.value = value;
    }
}
