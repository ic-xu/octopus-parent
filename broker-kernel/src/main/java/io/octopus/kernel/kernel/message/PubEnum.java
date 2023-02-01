package io.octopus.kernel.kernel.message;

/**
 * @author chenxu
 * @version 1
 * @date 2022/1/5 8:15 下午
 */
public enum PubEnum {
    /**
     * 不同质量的消息体
     */
    PUBLISH((byte)0),
    PUB_ACK((byte)1),
    PUB_REC((byte)2),
    PUB_REL((byte)3),
    PUB_COMP((byte)4);

    private final byte value;

    PubEnum(byte value) {
        this.value = value;
    }



    public static PubEnum valueOf(byte value) {
        return switch (value) {
            case 0 -> PUBLISH;
            case 1 -> PUB_ACK;
            case 2 -> PUB_REC;
            case 3 -> PUB_REL;
            case 4 -> PUB_COMP;
            default -> throw new IllegalArgumentException("invalid QoS: " + value);
        };
    }


    public byte getValue() {
        return value;
    }

}
