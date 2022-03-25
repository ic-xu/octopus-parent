package client;

import io.netty.channel.Channel;


public class Session {

    private Channel channel;


    public Session(Channel channel) {
        this.channel = channel;
    }


    public Channel getChannel() {
        return channel;
    }
}
