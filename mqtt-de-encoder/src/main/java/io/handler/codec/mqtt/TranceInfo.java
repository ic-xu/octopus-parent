package io.handler.codec.mqtt;


import io.handler.codec.mqtt.id.SnowflakeIdWorker;

public class TranceInfo {

    private final Long mqttMessageTranceId;

    public long getMqttMessageTranceId() {
        return mqttMessageTranceId;
    }

    public TranceInfo() {
        mqttMessageTranceId = SnowflakeIdWorker.getInstance().nextId();
    }

    public TranceInfo(Long mqttMessageTranceId) {
        this.mqttMessageTranceId = mqttMessageTranceId;
    }


}
