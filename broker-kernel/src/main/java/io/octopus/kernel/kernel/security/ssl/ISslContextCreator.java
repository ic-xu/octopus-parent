package io.octopus.kernel.kernel.security.ssl;

import io.netty.handler.ssl.SslContext;

/**
 * SSL certificate loader used to open SSL connections (websocket and MQTT-S).
 */
public interface ISslContextCreator {

    SslContext initSSLContext();
}
