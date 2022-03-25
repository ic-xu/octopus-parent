package io.octopus.udp.message;

import java.net.DatagramSocket;
import java.nio.channels.Selector;

public class DatagramSocketWrapper {
    private DatagramSocket socket;

    private Selector selector;

    public DatagramSocketWrapper(DatagramSocket socket, Selector selector) {
        this.socket = socket;
        this.selector = selector;
    }


    public DatagramSocket getSocket() {
        return socket;
    }

    public Selector getSelector() {
        return selector;
    }
}
