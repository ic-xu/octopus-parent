package io.octopus.kernel.kernel.message;
import java.util.Objects;

/**
 * @author chenxu
 * @version 1
 * @date 2022/1/5 8:14 下午
 */
public class KernelMessage {

    private  short packageId;



    private final PubEnum pubEnum;

    public KernelMessage(short packageId, PubEnum pubEnum) {
        this.packageId = packageId;
        this.pubEnum = pubEnum;
    }




    public PubEnum getPubEnum() {
        return pubEnum;
    }


    public void setPackageId(short packageId) {
        this.packageId = packageId;
    }

    public Short packageId() {
        return packageId;
    }



    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof KernelPayloadMessage)) {
            return false;
        }
        KernelMessage kernelMsg = (KernelMessage) o;
        return Objects.equals(packageId(), kernelMsg.packageId())
                && Objects.equals(kernelMsg.pubEnum,this.pubEnum);
    }

    @Override
    public int hashCode() {
        return Objects.hash(packageId());
    }



}
