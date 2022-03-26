package io.octopus.base.interfaces;

import io.netty.handler.ssl.SslContext;

/**
 * SSL certificate loader used to open SSL connections (websocket and MQTT-S).
 * @author user
 */
public interface ISslContextCreator {

    /**
     *
     * @return
     */
    SslContext initSSLContext();
}
