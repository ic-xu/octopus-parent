package io.octopus.kernel.exception;

/**
 * @author chenxu
 * @version 1
 * @date 2022/6/21 16:28
 */
public class NoSuchObjectException extends Exception{

    public NoSuchObjectException() {
        super("没有当前对象");
    }


    public NoSuchObjectException(String message) {
        super(message);
    }
}
