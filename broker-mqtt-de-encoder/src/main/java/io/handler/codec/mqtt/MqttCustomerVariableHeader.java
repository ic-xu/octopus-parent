package io.handler.codec.mqtt;


/**
 * Variable header of {@link MqttConnectMessage}
 */
public final class MqttCustomerVariableHeader {
    private int packageId;


    public int getPackageId() {
        return packageId;
    }


    public void setPackageId(short packageId) {
        this.packageId = packageId;
    }

    public MqttCustomerVariableHeader(int packageId){

        this.packageId = packageId;
    }
}
