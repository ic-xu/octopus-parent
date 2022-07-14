package io.octopus.kernel.kernel.contants;

import java.io.File;

public interface BrokerConstants {

    String INTERCEPT_HANDLER_PROPERTY_NAME = "postoffice.intercept";
    String BROKER_INTERCEPTOR_THREAD_POOL_SIZE = "intercept.thread_pool.size";
    String PERSISTENT_STORE_PROPERTY_NAME = "persistent_store";
    String AUTOSAVE_INTERVAL_PROPERTY_NAME = "autosave_interval";
    String PASSWORD_FILE_PROPERTY_NAME = "password_file";
    String PORT_PROPERTY_NAME = "tcp.port";
    String UDP_PORT_PROPERTY_NAME="udp.port";
    Integer UDP_TRANSPORT_DEFAULT_PORT =25522;
    String HOST_PROPERTY_NAME = "host";
     String MQTT_SUBPROTOCOL_CSV_LIST = "mqtt, mqttv3.1, mqttv3.1.1";
    String PLAIN_MQTT_PROTO = "TCP MQTT";
    String SSL_MQTT_PROTO = "SSL MQTT";

    String DEFAULT_OCTOPUS_STORE_H2_DB_FILENAME = "octopus_store.h2";
    String DEFAULT_PERSISTENT_PATH = System.getProperty("user.dir") + File.separator
            + DEFAULT_OCTOPUS_STORE_H2_DB_FILENAME;
    String WEB_SOCKET_PORT_PROPERTY_NAME = "websocket_port";
    String WSS_PORT_PROPERTY_NAME = "secure_websocket_port";
    String WEB_SOCKET_PATH_PROPERTY_NAME = "websocket_path";
    String WEB_SOCKET_MAX_FRAME_SIZE_PROPERTY_NAME = "websocket_max_frame_size";

    /**
     * Defines the SSL implementation to use, default to "JDK".
     *
     * @see io.netty.handler.ssl.SslProvider#name()
     */
    String SSL_PROVIDER = "ssl_provider";
    String SSL_PORT_PROPERTY_NAME = "ssl_port";
    String JKS_PATH_PROPERTY_NAME = "jks_path";

    /**
     * @see java.security.KeyStore#getInstance(String) for allowed types, default to "jks"
     */
    String KEY_STORE_TYPE = "key_store_type";
    String KEY_STORE_PASSWORD_PROPERTY_NAME = "key_store_password";
    String KEY_MANAGER_PASSWORD_PROPERTY_NAME = "key_manager_password";
    String ALLOW_ANONYMOUS_PROPERTY_NAME = "allow_anonymous";
    String REAUTHORIZE_SUBSCRIPTIONS_ON_CONNECT = "reauthorize_subscriptions_on_connect";
    String ALLOW_ZERO_BYTE_CLIENT_ID_PROPERTY_NAME = "allow_zero_byte_client_id";
    String ACL_FILE_PROPERTY_NAME = "acl_file";
    String AUTHORIZATOR_CLASS_NAME = "authorizator_class";
    String AUTHENTICATOR_CLASS_NAME = "authenticator_class";
    String DB_AUTHENTICATOR_DRIVER = "authenticator.db.driver";
    String DB_AUTHENTICATOR_URL = "authenticator.db.url";
    String DB_AUTHENTICATOR_QUERY = "authenticator.db.query";
    String DB_AUTHENTICATOR_DIGEST = "authenticator.db.digest";
    int PORT = 1883;
    int WEBSOCKET_PORT = 8080;
    String WEBSOCKET_PATH = "/mqtt";
    String DISABLED_PORT_BIND = "disabled";
    String HOST = "0.0.0.0";
    String NEED_CLIENT_AUTH = "need_client_auth";
    String NETTY_SO_BACKLOG_PROPERTY_NAME = "netty.so_backlog";
    String NETTY_SO_REUSEADDR_PROPERTY_NAME = "netty.so_reuseaddr";
    String NETTY_TCP_NODELAY_PROPERTY_NAME = "netty.tcp_nodelay";
    String NETTY_SO_KEEPALIVE_PROPERTY_NAME = "netty.so_keepalive";
    String NETTY_CHANNEL_TIMEOUT_SECONDS_PROPERTY_NAME = "netty.channel_timeout.seconds";
    String NETTY_EPOLL_PROPERTY_NAME = "netty.epoll";
    String NETTY_MAX_BYTES_PROPERTY_NAME = "netty.mqtt.message_size";
    int DEFAULT_NETTY_MAX_BYTES_IN_MESSAGE = 8092;
    String IMMEDIATE_BUFFER_FLUSH_PROPERTY_NAME = "immediate_buffer_flush";
    String OPEN_NETTY_LOGGER = "open_netty_logger";
    String REGISTER_CENTER_USER = "register_center_user";
    String METRICS_ENABLE_PROPERTY_NAME = "use_metrics";
    String METRICS_LIBRATO_EMAIL_PROPERTY_NAME = "metrics.librato.email";
    String METRICS_LIBRATO_TOKEN_PROPERTY_NAME = "metrics.librato.token";
    String METRICS_LIBRATO_SOURCE_PROPERTY_NAME = "metrics.librato.source";

    String BUGSNAG_ENABLE_PROPERTY_NAME = "use_bugsnag";
    String BUGSNAG_TOKEN_PROPERTY_NAME = "bugsnag.token";

    String STORAGE_CLASS_NAME = "storage_class";

    String HTTP_PORT = "http.port";
    String HTTPS_PORT = "https.port";

    int FLIGHT_BEFORE_RESEND_MS = 5_000;
    int INFLIGHT_WINDOW_SIZE = 10;

    String DATA_DIR = "data" + File.separator;
    String DATA_QUEUE = DATA_DIR+"queue"+File.separator;
    String DATA_CHECK_POINT = DATA_DIR+"checkPoint";
    String DATABASES_TYPE="databases_type";
    String LEVEL_DB="levelDB";
    String H2="h2";
    String MEMORY="memory";
}
