package io.octopus.udp.message;


import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import java.util.concurrent.atomic.AtomicInteger;

public class MessagePackage {

    private Long messageid;
    private ByteBuf[] byteBufs;
    private AtomicInteger byteSize ;
    private Integer messageSize;


    public MessagePackage(Long messageid, int sqSize,Integer messageSize){
        this.messageid=messageid;
        this.messageSize = messageSize;
        byteBufs = new ByteBuf[sqSize];
        byteSize = new AtomicInteger(0);
    }

    public boolean add(int sqmentId,ByteBuf byteBuf){
        if(null == byteBufs[sqmentId]){
            byteSize.addAndGet(byteBuf.readableBytes());
            byteBufs[sqmentId] = byteBuf;
        }
        if(messageSize==byteSize.get())
            return true;
        else return false;
    }

    public Long getMessageid() {
        return messageid;
    }

    public void setMessageid(Long messageid) {
        this.messageid = messageid;
    }

    public ByteBuf getAllMessage(){
        return Unpooled.wrappedBuffer(byteBufs);
    }
}
