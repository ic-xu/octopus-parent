package io.handler.codec.mqtt.utils;

import io.handler.codec.mqtt.MqttVersion;

/**
 * @author user
 */
public class MqttVersionUtils {



    /**
     * if have mqttVersionLevel ,return ture,else return false
     * @param mqttVersionLevel mqtt  version level
     * @return boolean
     */
    public static boolean isMatchVersion(byte mqttVersionLevel) {
        return mqttVersionLevel == 2 || mqttVersionLevel == 3 || mqttVersionLevel == 4;
    }


    private static boolean isNotProtocolVersion(byte mqttVersionLevel, MqttVersion version) {
        return mqttVersionLevel != version.protocolLevel();
    }
}
