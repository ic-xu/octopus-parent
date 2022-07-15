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
    PUBLISH(0),
    PUB_ACK(1),
    PUB_REC(2),
    PUB_REL(3),
    PUB_COMP(4);

    private final int value;

    PubEnum(int value) {
        this.value = value;
    }


    public static PubEnum valueOf(int value) {
        switch (value) {
            case 0:
                return PUBLISH;
            case 1:
                return PUB_ACK;
            case 2:
                return PUB_REC;
            case 3:
                return PUB_REL;
            case 4:
                return PUB_COMP;
            default:
                throw new IllegalArgumentException("invalid QoS: " + value);
        }
    }


    public int getValue() {
        return value;
    }

}
