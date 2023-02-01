package io.octopus.kernel.kernel.message;

import java.io.IOException;

/**
 * @author chenxu
 * @version 1
 * @date 2022/6/21 16:06
 */
public interface IMessage  extends IPackageId{


    /**
     * 返回消息大小
     * @return 消息大小
     */
    Integer getSize() throws IOException;



    byte[] toByteArr() throws IOException;


    MsgQos getQos();
}
