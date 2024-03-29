package io.octopus.kernel.kernel.message;

/**
 * @author chenxu
 * @version 1
 * @date 2022/6/21 16:25
 */
public class PacketIPackageId implements IPackageId {
    private final Long alongId;

    private final Short aShortId;

    public PacketIPackageId(Long alongId, Short aShortId) {
        this.alongId = alongId;
        this.aShortId = aShortId;
    }


    @Override
    public Long longId() {
        return alongId;
    }

    @Override
    public Short shortId() {
        return aShortId;
    }
}
