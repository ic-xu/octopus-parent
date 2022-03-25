package io.octopus.interception;

import io.handler.codec.mqtt.MqttConnectMessage;
import io.handler.codec.mqtt.MqttCustomerMessage;
import io.handler.codec.mqtt.MqttPublishMessage;
import io.octopus.broker.config.IConfig;
import io.octopus.broker.subscriptions.Subscription;
import io.octopus.contants.BrokerConstants;
import io.octopus.interception.messages.*;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.DefaultThreadFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static io.octopus.utils.LoggingUtils.getInterceptorIds;

/**
 * An interceptor that execute the interception tasks asynchronously.
 */
public final class BrokerNotifyInterceptor implements NotifyInterceptor {

    private static final Logger LOGGER = LoggerFactory.getLogger(BrokerNotifyInterceptor.class);
    private final Map<Class<?>, List<InterceptHandler>> handlers;
    private final ExecutorService executor;

    private BrokerNotifyInterceptor(int poolSize, List<InterceptHandler> handlers) {
        LOGGER.info("Initializing broker interceptor. InterceptorIds={}", getInterceptorIds(handlers));
        this.handlers = new HashMap<>();
        for (Class<?> messageType : InterceptHandler.ALL_MESSAGE_TYPES) {
            this.handlers.put(messageType, new CopyOnWriteArrayList<InterceptHandler>());
        }
        for (InterceptHandler handler : handlers) {
            this.addInterceptHandler(handler);
        }
        executor = Executors.newFixedThreadPool(poolSize, new DefaultThreadFactory("intercept-threads"));
    }

    /**
     * Configures a broker interceptor, with a thread pool of one thread.
     *
     * @param handlers InterceptHandlers listeners.
     */
    public BrokerNotifyInterceptor(List<InterceptHandler> handlers) {
        this(1, handlers);
    }

    /**
     * Configures a broker interceptor using the pool size specified in the IConfig argument.
     *
     * @param props    configuration properties.
     * @param handlers InterceptHandlers listeners.
     */
    public BrokerNotifyInterceptor(IConfig props, List<InterceptHandler> handlers) {
        this(Integer.parseInt(props.getProperty(BrokerConstants.BROKER_INTERCEPTOR_THREAD_POOL_SIZE, "1")), handlers);
    }

    /**
     * Shutdown graciously the executor service
     */
    public void stop() {
        LOGGER.info("Shutting down interceptor thread pool...");
        executor.shutdown();
        try {
            LOGGER.info("Waiting for thread pool tasks to terminate...");
            executor.awaitTermination(10L, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
        }
        if (!executor.isTerminated()) {
            LOGGER.warn("Forcing shutdown of interceptor thread pool...");
            executor.shutdownNow();
        }
        LOGGER.info("interceptors stopped");
    }

    @Override
    public void notifyCustomer(MqttCustomerMessage msg) {

    }

    @Override
    public void notifyClientConnected(final MqttConnectMessage msg) {
        for (final InterceptHandler handler : this.handlers.get(InterceptConnectMessage.class)) {
            LOGGER.debug("Sending MQTT CONNECT message to interceptor. CId={}, interceptorId={}",
                    msg.payload().clientIdentifier(), handler.getID());
            executor.execute(() -> handler.onConnect(new InterceptConnectMessage(msg)));
        }
    }

    @Override
    public void notifyClientDisconnected(final String clientID, final String username) {
        for (final InterceptHandler handler : this.handlers.get(InterceptDisconnectMessage.class)) {
            LOGGER.debug("Notifying MQTT client disconnection to interceptor. CId={}, username={}, interceptorId={}",
                    clientID, username, handler.getID());
            executor.execute(() -> handler.onDisconnect(new InterceptDisconnectMessage(clientID, username)));
        }
    }

    @Override
    public void notifyClientConnectionLost(final String clientID, final String username) {
        for (final InterceptHandler handler : this.handlers.get(InterceptConnectionLostMessage.class)) {
            LOGGER.debug("Notifying unexpected MQTT client disconnection to interceptor CId={}, username={}, " +
                    "interceptorId={}", clientID, username, handler.getID());
            executor.execute(() -> handler.onConnectionLost(new InterceptConnectionLostMessage(clientID, username)));
        }
    }

    @Override
    public void notifyTopicBeforePublished(MqttPublishMessage msg) {
        msg.retain();
        executor.execute(() -> {
            try {
                for (InterceptHandler handler : handlers.get(InterceptPublishMessage.class)) {
                    handler.onBeforePublish(msg);
                }
            } finally {
                ReferenceCountUtil.release(msg);
            }
        });
    }

    @Override
    public void notifyTopicPublished(final MqttPublishMessage msg, final String clientID, final String username) {
        msg.retain();

        executor.execute(() -> {
            try {
                int messageId = msg.variableHeader().packetId();
                String topic = msg.variableHeader().topicName();
                for (InterceptHandler handler : handlers.get(InterceptPublishMessage.class)) {
                    LOGGER.debug("Notifying MQTT PUBLISH message to interceptor. CId={}, messageId={}, topic={}, "
                            + "interceptorId={}", clientID, messageId, topic, handler.getID());
                    handler.onPublish(new InterceptPublishMessage(msg.retainedDuplicate(), clientID, username));
                }
            } finally {
                ReferenceCountUtil.release(msg);
            }
        });
    }

    @Override
    public void notifyTopicSubscribed(final Subscription sub, final String username) {
        for (final InterceptHandler handler : this.handlers.get(InterceptSubscribeMessage.class)) {
            LOGGER.debug("Notifying MQTT SUBSCRIBE message to interceptor. CId={}, topicFilter={}, interceptorId={}",
                    sub.getClientId(), sub.getTopicFilter(), handler.getID());
            executor.execute(() -> handler.onSubscribe(new InterceptSubscribeMessage(sub, username)));
        }
    }

    @Override
    public void notifyTopicUnsubscribed(final String topic, final String clientID, final String username) {
        for (final InterceptHandler handler : this.handlers.get(InterceptUnsubscribeMessage.class)) {
            LOGGER.debug("Notifying MQTT UNSUBSCRIBE message to interceptor. CId={}, topic={}, interceptorId={}", clientID,
                    topic, handler.getID());
            executor.execute(() -> handler.onUnsubscribe(new InterceptUnsubscribeMessage(topic, clientID, username)));
        }
    }

    @Override
    public void notifyMessageAcknowledged(final InterceptAcknowledgedMessage msg) {
        for (final InterceptHandler handler : this.handlers.get(InterceptAcknowledgedMessage.class)) {
            LOGGER.debug("Notifying MQTT ACK message to interceptor. CId={}, messageId={}, topic={}, interceptorId={}",
                    msg.getMsg()/*.getClientID()*/, msg.getPacketID(), msg.getTopic(), handler.getID());
            executor.execute(() -> handler.onMessageAcknowledged(msg));
        }
    }

    @Override
    public void addInterceptHandler(InterceptHandler interceptHandler) {
        Class<?>[] interceptedMessageTypes = getInterceptedMessageTypes(interceptHandler);
        LOGGER.info("Adding MQTT message interceptor. InterceptorId={}, handledMessageTypes={}",
                interceptHandler.getID(), interceptedMessageTypes);
        for (Class<?> interceptMessageType : interceptedMessageTypes) {
            this.handlers.get(interceptMessageType).add(interceptHandler);
        }
    }

    @Override
    public void removeInterceptHandler(InterceptHandler interceptHandler) {
        Class<?>[] interceptedMessageTypes = getInterceptedMessageTypes(interceptHandler);
        LOGGER.info("Removing MQTT message interceptor. InterceptorId={}, handledMessageTypes={}",
                interceptHandler.getID(), interceptedMessageTypes);
        for (Class<?> interceptMessageType : interceptedMessageTypes) {
            this.handlers.get(interceptMessageType).remove(interceptHandler);
        }
    }

    private static Class<?>[] getInterceptedMessageTypes(InterceptHandler interceptHandler) {
        Class<?>[] interceptedMessageTypes = interceptHandler.getInterceptedMessageTypes();
        if (interceptedMessageTypes == null) {
            return InterceptHandler.ALL_MESSAGE_TYPES;
        }
        return interceptedMessageTypes;
    }
}
