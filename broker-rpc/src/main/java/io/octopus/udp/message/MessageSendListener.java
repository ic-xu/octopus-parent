package io.octopus.udp.message;


public interface MessageSendListener {

    void onSuccess(MessageWrapper messageWrapper);

    void onError(Exception e);
}
