package io.octopus.kernel.config;

import io.octopus.kernel.contants.BrokerConstants;

public class BrokerConfiguration {

    private final boolean allowAnonymous;
    private final boolean allowZeroByteClientId;
    private final boolean reauthorizeSubscriptionsOnConnect;
    private final boolean immediateBufferFlush;
    private final boolean nettyLogger;

    public BrokerConfiguration(IConfig props) {
        allowAnonymous = props.boolProp(BrokerConstants.ALLOW_ANONYMOUS_PROPERTY_NAME, true);
        allowZeroByteClientId = props.boolProp(BrokerConstants.ALLOW_ZERO_BYTE_CLIENT_ID_PROPERTY_NAME, false);
        reauthorizeSubscriptionsOnConnect = props.boolProp(BrokerConstants.REAUTHORIZE_SUBSCRIPTIONS_ON_CONNECT, false);
        immediateBufferFlush = props.boolProp(BrokerConstants.IMMEDIATE_BUFFER_FLUSH_PROPERTY_NAME, false);
        nettyLogger = props.boolProp(BrokerConstants.OPEN_NETTY_LOGGER, false);
    }

    public BrokerConfiguration(boolean allowAnonymous, boolean allowZeroByteClientId,
                               boolean reauthorizeSubscriptionsOnConnect, boolean immediateBufferFlush) {
        this.allowAnonymous = allowAnonymous;
        this.allowZeroByteClientId = allowZeroByteClientId;
        this.reauthorizeSubscriptionsOnConnect = reauthorizeSubscriptionsOnConnect;
        this.immediateBufferFlush = immediateBufferFlush;
        this.nettyLogger = false;
    }

    public boolean isAllowAnonymous() {
        return allowAnonymous;
    }

    public boolean isAllowZeroByteClientId() {
        return allowZeroByteClientId;
    }

    public boolean isReauthorizeSubscriptionsOnConnect() {
        return reauthorizeSubscriptionsOnConnect;
    }

    public boolean isImmediateBufferFlush() {
        return immediateBufferFlush;
    }


    public boolean isOpenNettyLogger() {
        return nettyLogger;
    }
}
