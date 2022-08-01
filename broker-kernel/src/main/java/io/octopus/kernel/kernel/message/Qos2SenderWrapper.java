package io.octopus.kernel.kernel.message;

public class Qos2SenderWrapper {

    private KernelMessage qos2Msg;


    private Boolean receiverPubRec;


    public Qos2SenderWrapper(KernelMessage qos2Msg) {
        this.qos2Msg = qos2Msg;
        this.receiverPubRec = false;
    }

    public KernelMessage getQos2Msg() {
        return qos2Msg;
    }

    public void setQos2Msg(KernelMessage qos2Msg) {
        this.qos2Msg = qos2Msg;
    }

    public Boolean getReceiverPubRec() {
        return receiverPubRec;
    }

    public void setReceiverPubRec(Boolean receiverPubRec) {
        this.receiverPubRec = receiverPubRec;
    }
}
