package io.octopus.kernel.kernel.message;

import io.octopus.kernel.kernel.subscriptions.Subscription;

/**
 * @author chenxu
 * @version 1
 * @date 2022/1/5 8:15 下午
 */
public enum MsgQos {
    /**
     * 不同质量的消息体
     */
    AT_MOST_ONCE(0),
    AT_LEAST_ONCE(1),
    EXACTLY_ONCE(2),
    UDP(3),
    FAILURE(4);

    private final int value;

    MsgQos(int value) {
        this.value = value;
    }


    public static MsgQos valueOf(int value) {
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



    /**
     * compare qos and get the less one
     *
     * @param subscription sub
     * @param qos qos
     * @return qos
     */
    public static MsgQos lowerQosToTheSubscriptionDesired(Subscription subscription, MsgQos qos){
        MsgQos newQos = qos;
        if(qos.getValue()>subscription.getRequestedQos().getValue()){
            newQos = subscription.getRequestedQos();
        }
        return newQos;
    }

}
