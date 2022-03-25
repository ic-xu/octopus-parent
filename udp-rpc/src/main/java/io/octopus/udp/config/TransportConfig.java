package io.octopus.udp.config;

import java.util.HashMap;
import java.util.Map;


public class TransportConfig {

    Map<String,String> properties = new HashMap<>();

    public TransportConfig(){
        properties.put("udp.transport.host","127.0.0.1");
        properties.put("udp.transport.port","2522");
        properties.put("udp.transport.segment-size","512");
        properties.put("udp.transport.epoll","false");
        properties.put("udp.transport.sender-pool-size","10");
        properties.put("udp.transport.segment.send-time-out","5000");
        properties.put("udp.transport.segment.send-time-out-count","4");
        properties.put("udp.transport.message.receiver-time-out","20000");
    }


    public void put(String key ,String value){
        properties.put(key,value);
    }


    public String getProperties(String key){
        return properties.get(key);
    }

    public Boolean getBooleanProperties(String key){
        return Boolean.parseBoolean(key);
    }


    public Integer getIntegerProperties(String key,Integer defaultValues){
        try {
            return Integer.parseInt(properties.get(key));
        }catch (Exception ignored){
            return defaultValues;
        }
    }

    public Long getLongProperties(String key,Long defaultValues){
        try {
            return Long.parseLong(properties.get(key));
        }catch (Exception ignored){
            return defaultValues;
        }
    }
}
