package io.octopus.kernel.kernel.config;

import io.octopus.kernel.kernel.contants.BrokerConstants;
import io.octopus.kernel.kernel.Environment;

/**
 * Base interface for all configuration
 */
public abstract class IConfig implements Environment {

    public static final String DEFAULT_CONFIG = "config/octopus.conf";

    public abstract void setProperty(String name, String value);

    /**
     * Same semantic of Properties
     *
     * @param name property name.
     * @return property value.
     */
    public abstract String getProperty(String name);

    /**
     * Same semantic of Properties
     *
     * @param name         property name.
     * @param defaultValue default value to return in case the property doesn't exists.
     * @return property value.
     */
    public abstract String getProperty(String name, String defaultValue);

    /**
     * Same semantic of Properties
     *
     * @param name         property name.
     * @param defaultValue default value to return in case the property doesn't exists.
     * @return property value.
     */
    public abstract Integer getIntegerProperty(String name, Integer defaultValue);


    /**
     * Same semantic of Properties
     *
     * @param name         property name.
     * @param defaultValue default value to return in case the property doesn't exists.
     * @return property value.
     */
    public abstract Long getLongProperty(String name, Long defaultValue);

    void assignDefaults() {
        setProperty(BrokerConstants.PORT_PROPERTY_NAME, Integer.toString(BrokerConstants.PORT));
        setProperty(BrokerConstants.HOST_PROPERTY_NAME, BrokerConstants.HOST);
        // setProperty(BrokerConstants.WEB_SOCKET_PORT_PROPERTY_NAME,
        // Integer.toString(BrokerConstants.WEBSOCKET_PORT));
        setProperty(BrokerConstants.PASSWORD_FILE_PROPERTY_NAME, "");
        // setProperty(BrokerConstants.PERSISTENT_STORE_PROPERTY_NAME,
        // BrokerConstants.DEFAULT_PERSISTENT_PATH);
        setProperty(BrokerConstants.ALLOW_ANONYMOUS_PROPERTY_NAME, Boolean.TRUE.toString());
        setProperty(BrokerConstants.AUTHENTICATOR_CLASS_NAME, "");
        setProperty(BrokerConstants.AUTHORIZATOR_CLASS_NAME, "");
        setProperty(BrokerConstants.NETTY_MAX_BYTES_PROPERTY_NAME,
                String.valueOf(BrokerConstants.DEFAULT_NETTY_MAX_BYTES_IN_MESSAGE));
    }

    public abstract IResourceLoader getResourceLoader();

    public int intProp(String propertyName, int defaultValue) {
        String propertyValue = getProperty(propertyName);
        if (propertyValue == null) {
            return defaultValue;
        }
        return Integer.parseInt(propertyValue);
    }

    public boolean boolProp(String propertyName, boolean defaultValue) {
        String propertyValue = getProperty(propertyName);
        if (propertyValue == null) {
            return defaultValue;
        }
        return Boolean.parseBoolean(propertyValue);
    }
}
