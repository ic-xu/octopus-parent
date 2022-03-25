package io.octopus.broker.customer;

public interface MqttCustomerMessageType {

    byte listClientIds = 0;
    byte newVideo=1;
    byte inviteVideo = 2;
    byte ring = 3;
    byte cancel = 4;
    byte reject = 5;
    byte candidate = 6;
    byte offer = 7;
    byte answer = 8;
    byte leave = 9;
    byte disconnect = 10;
    byte audio=11;
    byte chat = 21;
}
