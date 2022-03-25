package io.octopus.base.message;

/**
 * @author chenxu
 * @version 1
 * @date 2022/1/5 8:15 下午
 */
public enum MessageQos {

    AT_MOST_ONCE(0),
    AT_LEAST_ONCE(1),
    EXACTLY_ONCE(2),
    UDP(3);

    private final int value;

    MessageQos(int value) {
        this.value = value;
    }


    public static MessageQos valueOf(int value) {
        switch (value) {
            case 0:
                return AT_MOST_ONCE;
            case 1:
                return AT_LEAST_ONCE;
            case 2:
                return EXACTLY_ONCE;
            case 3:
                return UDP;
            default:
                throw new IllegalArgumentException("invalid QoS: " + value);
        }
    }


    public int getValue() {
        return value;
    }
}
