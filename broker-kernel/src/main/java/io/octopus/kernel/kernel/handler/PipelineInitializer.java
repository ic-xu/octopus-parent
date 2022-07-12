package io.octopus.kernel.kernel.handler;

import io.netty.channel.socket.SocketChannel;

/**
 * @author user
 */
public interface PipelineInitializer {

    /*
     * config channel Pipeline
     */
    void init(SocketChannel channel);
}
