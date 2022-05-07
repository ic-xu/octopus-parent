package io.octopus.broker.customer;

/**
 * @author user
 */
public interface MqttCustomerMessageType {

    /**
     * signalling
     */
    byte SIGNALLING = 10;

    byte LIST_CLIENT_IDS = 0;
    byte NEW_VIDEO = 1;
    byte INVITE_VIDEO = 2;
    byte RING = 3;
    byte CANCEL = 4;
    byte REJECT = 5;
    byte CANDIDATE = 6;
    byte OFFER = 7;
    byte ANSWER = 8;
    byte LEAVE = 9;
//    byte DISCONNECT = 10;
    byte AUDIO = 11;
    byte CHAT = 21;
}
