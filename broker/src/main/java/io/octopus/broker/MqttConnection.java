package io.octopus.broker;

import io.octopus.broker.config.BrokerConfiguration;
import io.octopus.broker.exception.SessionCorruptedException;
import io.octopus.broker.handler.CustomerHandler;
import io.octopus.broker.handler.InflictReSenderHandler;
import io.octopus.broker.subscriptions.Topic;
import io.octopus.broker.security.IAuthenticator;
import io.octopus.interception.BrokerNotifyInterceptor;
import io.octopus.utils.DebugUtils;
import io.octopus.utils.NettyUtils;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelPipeline;
import io.handler.codec.mqtt.*;
import io.netty.handler.timeout.IdleStateHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static io.netty.channel.ChannelFutureListener.CLOSE_ON_FAILURE;
import static io.netty.channel.ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE;
import static io.handler.codec.mqtt.MqttConnectReturnCode.*;
import static io.handler.codec.mqtt.MqttMessageIdVariableHeader.from;
import static io.handler.codec.mqtt.MqttQoS.*;

/**
 * message handle core
 */
public final class MqttConnection {

    private static final Logger LOGGER = LoggerFactory.getLogger(MqttConnection.class);

    final Channel channel;
    private final BrokerConfiguration brokerConfig;
    private final IAuthenticator authenticator;
    private final SessionRegistry sessionRegistry;
    private final PostOffice postOffice;
    private volatile boolean connected;
    private static final AtomicInteger lastPacketId = new AtomicInteger(1);
    private Session boundSession;
    private BrokerNotifyInterceptor interceptor;

    MqttConnection(Channel channel, BrokerConfiguration brokerConfig, IAuthenticator authenticator,
                   SessionRegistry sessionRegistry, PostOffice postOffice) {
        this.channel = channel;
        this.brokerConfig = brokerConfig;
        this.authenticator = authenticator;
        this.sessionRegistry = sessionRegistry;
        this.postOffice = postOffice;
        this.connected = false;
    }

    MqttConnection(Channel channel, BrokerConfiguration brokerConfig, IAuthenticator authenticator,
                   SessionRegistry sessionRegistry, PostOffice postOffice, BrokerNotifyInterceptor interceptor) {
        this.channel = channel;
        this.brokerConfig = brokerConfig;
        this.authenticator = authenticator;
        this.sessionRegistry = sessionRegistry;
        this.postOffice = postOffice;
        this.connected = false;
        this.interceptor = interceptor;
    }


    public Session getBoundSession() {
        return boundSession;
    }

    public SessionRegistry getSessionRegistry() {
        return sessionRegistry;
    }

    public PostOffice getPostOffice() {
        return postOffice;
    }


    public void handleMessage(MqttMessage msg) {
        MqttMessageType messageType = msg.fixedHeader().messageType();
        LOGGER.debug("Received MQTT message, type: {}, channel: {}", messageType, channel);
        switch (messageType) {
            case CUSTOMER:
                processCustomer((MqttCustomerMessage) msg);
                break;
            case CONNECT:
                processConnect((MqttConnectMessage) msg);
                break;
            case SUBSCRIBE:
                processSubscribe((MqttSubscribeMessage) msg);
                break;
            case UNSUBSCRIBE:
                processUnsubscribe((MqttUnsubscribeMessage) msg);
                break;
            case PUBLISH:
                processPublish((MqttPublishMessage) msg);
                break;
            case PUBREC:
                processPubRec(msg);
                break;
            case PUBCOMP:
                processPubComp(msg);
                break;
            case PUBREL:
                processPubRel(msg);
                break;
            case DISCONNECT:
                processDisconnect(msg);
                break;
            case PUBACK:
                processPubAck(msg);
                break;
            case PINGREQ:
                processPing();
                break;
            default:
                LOGGER.error("Unknown MessageType: {}, channel: {}", messageType, channel);
                break;
        }
    }

    void processConnect(MqttConnectMessage msg) {
        MqttConnectPayload payload = msg.payload();
        String clientId = payload.clientIdentifier();
        final String username = payload.userName();
        LOGGER.trace("Processing CONNECT message. CId={} username: {} channel: {}", clientId, username, channel);

        if (isNotProtocolVersion(msg, MqttVersion.MQTT_3_1)
                && isNotProtocolVersion(msg, MqttVersion.MQTT_3_1_1)
                && isNotProtocolVersion(msg, MqttVersion.MQTT_2)) {
            LOGGER.warn("MQTT protocol version is not valid. CId={} channel: {}", clientId, channel);
            abortConnection(CONNECTION_REFUSED_UNACCEPTABLE_PROTOCOL_VERSION);
            return;
        }
        final boolean cleanSession = msg.variableHeader().isCleanSession();
        if (clientId == null || clientId.length() == 0) {
            if (!brokerConfig.isAllowZeroByteClientId()) {
                LOGGER.info("Broker doesn't permit MQTT empty client ID. Username: {}, channel: {}", username, channel);
                abortConnection(CONNECTION_REFUSED_IDENTIFIER_REJECTED);
                return;
            }

            if (!cleanSession) {
                LOGGER.info("MQTT client ID cannot be empty for persistent session. Username: {}, channel: {}",
                        username, channel);
                abortConnection(CONNECTION_REFUSED_IDENTIFIER_REJECTED);
                return;
            }

            // Generating client id.
            clientId = UUID.randomUUID().toString().replace("-", "");
            LOGGER.debug("Client has connected with integration generated id: {}, username: {}, channel: {}", clientId,
                    username, channel);
        }

        if (!login(msg, clientId)) {
            abortConnection(CONNECTION_REFUSED_BAD_USER_NAME_OR_PASSWORD);
            channel.close().addListener(CLOSE_ON_FAILURE);
            return;
        }

        final SessionRegistry.SessionCreationResult result;
        try {
            LOGGER.trace("Binding MQTTConnection (channel: {}) to session", channel);
            result = sessionRegistry.createOrReopenSession(msg, clientId, this.getUsername());
            result.session.bind(this);
            boundSession = result.session;
        } catch (SessionCorruptedException scex) {
            LOGGER.warn("MQTT session for client ID {} cannot be created, channel: {}", clientId, channel);
            abortConnection(CONNECTION_REFUSED_SERVER_UNAVAILABLE);
            return;
        }

        boolean isSessionAlreadyPresent = !cleanSession && result.alreadyStored;
        final String clientIdUsed = clientId;
        final MqttConnAckMessage ackMessage = MqttMessageBuilders.connAck()
                .returnCode(CONNECTION_ACCEPTED)
                .sessionPresent(isSessionAlreadyPresent).build();
        channel.writeAndFlush(ackMessage).addListener((ChannelFutureListener) future -> {
            if (future.isSuccess()) {
                LOGGER.trace("CONNACK sent, channel: {}", channel);
                if (!result.session.completeConnection()) {//change the session status
                    // send DISCONNECT and close the channel
                    final MqttMessage disconnectMsg = MqttMessageBuilders.disconnect().build();
                    channel.writeAndFlush(disconnectMsg).addListener(ChannelFutureListener.CLOSE);
                    LOGGER.warn("CONNACK is sent but the session created can't transition in CONNECTED state");
                } else {//clientIdUsed == clientId
                    NettyUtils.clientID(channel, clientIdUsed);
                    // 连接标记设置为true
                    connected = true;
                    // OK continue with sending queued messages and normal flow
                    //notify other offline
                    if (result.mode == SessionRegistry.CreationModeEnum.REOPEN_EXISTING) {
                        result.session.sendQueuedMessagesWhileOffline();
                    }

                    initializeKeepAliveTimeout(channel, msg, clientIdUsed);
                    setupInflictReSender(channel);

                    postOffice.dispatchConnection(msg);
                    LOGGER.trace("dispatch connection: {}", msg);
                }
            } else {
                boundSession.disconnect();
                sessionRegistry.remove(boundSession);
                LOGGER.error("CONNACK send failed, cleanup session and close the connection", future.cause());
                channel.close();
            }

        });
    }

    void processCustomer(MqttCustomerMessage msg) {
        CustomerHandler.processMessage(msg, this, sessionRegistry);
    }

    private void processPubComp(MqttMessage msg) {
        final int messageID = ((MqttMessageIdVariableHeader) msg.variableHeader()).messageId();
        boundSession.processPubComp(messageID);
    }

    private void processPubRec(MqttMessage msg) {
        final int messageID = ((MqttMessageIdVariableHeader) msg.variableHeader()).messageId();
        boundSession.processPubRec(messageID);
    }

    static MqttMessage pubRel(int messageID) {
        MqttFixedHeader pubRelHeader = new MqttFixedHeader(MqttMessageType.PUBREL, false, AT_LEAST_ONCE, false, 0);
        return new MqttPubRelMessage(pubRelHeader, from(messageID));
    }

    private void processPubAck(MqttMessage msg) {
        final int messageID = ((MqttMessageIdVariableHeader) msg.variableHeader()).messageId();
        boundSession.pubAckReceived(messageID);
    }


    private void processPing() {
        MqttFixedHeader pingHeader = new MqttFixedHeader(MqttMessageType.PINGRESP, false, AT_MOST_ONCE,
                false, 0);
        MqttMessage pingResp = new MqttMessage(pingHeader);
        channel.writeAndFlush(pingResp).addListener(CLOSE_ON_FAILURE);
    }

    private void setupInflictReSender(Channel channel) {
        channel.pipeline()
                .addFirst("inflictReSender", new InflictReSenderHandler(5_000, TimeUnit.MILLISECONDS));
    }

    private void initializeKeepAliveTimeout(Channel channel, MqttConnectMessage msg, String clientId) {
        int keepAlive = msg.variableHeader().keepAliveTimeSeconds();
        NettyUtils.keepAlive(channel, keepAlive);
        NettyUtils.cleanSession(channel, msg.variableHeader().isCleanSession());
        NettyUtils.clientID(channel, clientId);
        int idleTime = Math.round(keepAlive * 1.5f);
        setIdleTime(channel.pipeline(), idleTime);

        LOGGER.debug("Connection has been configured CId={}, keepAlive={}, removeTemporaryQoS2={}, idleTime={}",
                clientId, keepAlive, msg.variableHeader().isCleanSession(), idleTime);
    }

    private void setIdleTime(ChannelPipeline pipeline, int idleTime) {
        if (pipeline.names().contains("idleStateHandler")) {
            pipeline.remove("idleStateHandler");
        }
        pipeline.addFirst("idleStateHandler", new IdleStateHandler(idleTime, 0, 0));
    }

    private boolean isNotProtocolVersion(MqttConnectMessage msg, MqttVersion version) {
        return msg.variableHeader().version() != version.protocolLevel();
    }

    private void abortConnection(MqttConnectReturnCode returnCode) {
        MqttConnAckMessage badProto = MqttMessageBuilders.connAck()
                .returnCode(returnCode)
                .sessionPresent(false).build();
        channel.writeAndFlush(badProto).addListener(FIRE_EXCEPTION_ON_FAILURE);
        channel.close().addListener(CLOSE_ON_FAILURE);
    }

    private boolean login(MqttConnectMessage msg, final String clientId) {
        // handle user authentication
        if (msg.variableHeader().hasUserName()) {
            byte[] pwd = null;
            if (msg.variableHeader().hasPassword()) {
                pwd = msg.payload().passwordInBytes();
            } else if (!brokerConfig.isAllowAnonymous()) {
                LOGGER.info("Client didn't supply any password and MQTT anonymous mode is disabled CId={}", clientId);
                return false;
            }
            final String userNameString = msg.payload().userName();
            if (!authenticator.checkValid(clientId, userNameString, pwd)) {
                LOGGER.info("Authenticator has rejected the MQTT credentials CId={}, username={}", clientId, userNameString);
                return false;
            }
            NettyUtils.userName(channel, userNameString);
            sessionRegistry.registerUserName(userNameString, clientId);
        } else if (!brokerConfig.isAllowAnonymous()) {
            LOGGER.info("Client didn't supply any credentials and MQTT anonymous mode is disabled. CId={}", clientId);
            return false;
        }
        return true;
    }

    public void handleConnectionLost() {
        String clientID = NettyUtils.clientID(channel);
        String userName = NettyUtils.userName(channel);
        if (clientID == null || clientID.isEmpty()) {
            return;
        }
        LOGGER.info("Notifying connection lost event. CId: {}, channel: {}", clientID, channel);
        if (boundSession.hasWill()) {
            postOffice.fireWill(boundSession.getWill(),this);
        }
        if (boundSession.isClean()) {
            LOGGER.debug("Remove session for client CId: {}, channel: {}", clientID, channel);
            /*
             *清除用户注册信息
             */
            postOffice.cleanSubscribe(boundSession.getSubscriptions());
            sessionRegistry.removeUserClientIdByUsername(userName, clientID);
            sessionRegistry.remove(boundSession);
        } else {
            boundSession.disconnect();
        }
        connected = false;
        //dispatch connection lost to intercept.
        postOffice.dispatchConnectionLost(clientID, userName);
        LOGGER.trace("dispatch disconnection: clientId={}, userName={}", clientID, userName);
    }

    boolean isConnected() {
        return connected;
    }

    void dropConnection() {
        channel.close().addListener(FIRE_EXCEPTION_ON_FAILURE);
    }

    void processDisconnect(MqttMessage msg) {
        LOGGER.trace(msg.toString());
        final String clientID = NettyUtils.clientID(channel);
        LOGGER.trace("Start DISCONNECT CIInFlight(this)d={}, channel: {}", clientID, channel);
        if (!connected) {
            LOGGER.info("DISCONNECT received on already closed connection, CId={}, channel: {}", clientID, channel);
            return;
        }
        boundSession.disconnect();
        connected = false;
        channel.close().addListener(FIRE_EXCEPTION_ON_FAILURE);
        LOGGER.trace("Processed DISCONNECT CId={}, channel: {}", clientID, channel);
        String userName = NettyUtils.userName(channel);
        postOffice.dispatchDisconnection(clientID, userName);
        LOGGER.trace("dispatch disconnection: clientId={}, userName={}", clientID, userName);
    }

    void processSubscribe(MqttSubscribeMessage msg) {
        final String clientID = NettyUtils.clientID(channel);
        if (!connected) {
            LOGGER.warn("SUBSCRIBE received on already closed connection, CId={}, channel: {}", clientID, channel);
            dropConnection();
            return;
        }
        postOffice.subscribeClientToTopics(msg, clientID, NettyUtils.userName(channel), this);
    }

    void sendSubAckMessage(int messageID, MqttSubAckMessage ackMessage) {
        final String clientId = NettyUtils.clientID(channel);
        LOGGER.trace("Sending SUBACK response CId={}, messageId: {}", clientId, messageID);
        channel.writeAndFlush(ackMessage).addListener(FIRE_EXCEPTION_ON_FAILURE);
    }

    private void processUnsubscribe(MqttUnsubscribeMessage msg) {
        List<String> topics = msg.payload().topics();
        String clientID = NettyUtils.clientID(channel);

        LOGGER.trace("Processing UNSUBSCRIBE message. CId={}, topics: {}", clientID, topics);
        postOffice.unsubscribe(topics, this, msg.variableHeader().messageId());
    }

    void sendUnsubAckMessage(List<String> topics, String clientID, int messageID) {
        MqttFixedHeader fixedHeader = new MqttFixedHeader(MqttMessageType.UNSUBACK, false, AT_MOST_ONCE,
                false, 0);
        MqttUnsubAckMessage ackMessage = new MqttUnsubAckMessage(fixedHeader, from(messageID));

        LOGGER.trace("Sending UNSUBACK message. CId={}, messageId: {}, topics: {}", clientID, messageID, topics);
        channel.writeAndFlush(ackMessage).addListener(FIRE_EXCEPTION_ON_FAILURE);
        LOGGER.trace("Client <{}> unsubscribed from topics <{}>", clientID, topics);
    }

    void processPublish(MqttPublishMessage msg) {
        interceptor.notifyTopicBeforePublished(msg);

        final MqttQoS qos = msg.fixedHeader().qosLevel();
        final String username = NettyUtils.userName(channel);
        final String topicName = msg.variableHeader().topicName();
        final String clientId = getClientId();
        LOGGER.trace("Processing PUBLISH message. CId={}, topic: {}, messageId: {}, qos: {}", clientId, topicName,
                msg.variableHeader().packetId(), qos);
        final Topic topic = new Topic(topicName);
        if (!topic.isValid()) {
            LOGGER.debug("Drop connection because of invalid topic format");
            dropConnection();
        }
        switch (qos) {
            case AT_MOST_ONCE:
                postOffice.receivedPublishQos0(topic, username, clientId, msg);
                break;
            case AT_LEAST_ONCE: {
                final int messageID = msg.variableHeader().packetId();
                postOffice.receivedPublishQos1(this, topic, username, messageID, msg);
                break;
            }
            case EXACTLY_ONCE: {
                final int messageID = msg.variableHeader().packetId();
                //回复消息
                boundSession.receivedPublishQos2(messageID, msg);
                //发布
                postOffice.receivedPublishQos2(this, msg, username);
//                msg.release();
                break;
            }
            default:
                LOGGER.error("Unknown QoS-Type:{}", qos);
                break;
        }
    }

    void sendPublishReceived(int messageID) {
        LOGGER.trace("sendPubRec invoked on channel: {}", channel);
        MqttFixedHeader fixedHeader = new MqttFixedHeader(MqttMessageType.PUBREC, false, AT_MOST_ONCE,
                false, 0);
        MqttPubRecMessage pubRecMessage = new MqttPubRecMessage(fixedHeader, from(messageID));
        sendIfWritableElseDrop(pubRecMessage);
    }

    private void processPubRel(MqttMessage msg) {
        final int messageID = ((MqttMessageIdVariableHeader) msg.variableHeader()).messageId();
        boundSession.receivedPubRelQos2(messageID);
        sendPubCompMessage(messageID);
    }

    void sendPublish(MqttPublishMessage publishMsg) {
        final int packetId = publishMsg.variableHeader().packetId();
        final String topicName = publishMsg.variableHeader().topicName();
        final String clientId = getClientId();
        MqttQoS qos = publishMsg.fixedHeader().qosLevel();
        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace("Sending PUBLISH({}) message. MessageId={}, CId={}, topic={}, payload={}", qos, packetId,
                    clientId, topicName, DebugUtils.payload2Str(publishMsg.payload()));
        } else {
            LOGGER.debug("Sending PUBLISH({}) message. MessageId={}, CId={}, topic={}", qos, packetId, clientId,
                    topicName);
        }
        sendIfWritableElseDrop(publishMsg);
    }

    void sendIfWritableElseDrop(MqttMessage msg) {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("OUT {} on channel {}", msg.fixedHeader().messageType(), channel);
        }
        if (channel.isWritable()) {
            ChannelFuture channelFuture;
            if (brokerConfig.isImmediateBufferFlush()) {
                channelFuture = channel.writeAndFlush(msg);
            } else {
                channelFuture = channel.write(msg);
            }
            channelFuture.addListener(FIRE_EXCEPTION_ON_FAILURE);
        } else {
            LOGGER.error("channel is not writable");
        }
    }

    public void writabilityChanged() {
        if (channel.isWritable()) {
            LOGGER.debug("Channel {} is again writable", channel);
            boundSession.writabilityChanged();
        }
    }

    void sendPubAck(int messageID) {
        LOGGER.trace("sendPubAck invoked");
        MqttFixedHeader fixedHeader = new MqttFixedHeader(MqttMessageType.PUBACK, false, AT_MOST_ONCE,
                false, 0);
        MqttPubAckMessage pubAckMessage = new MqttPubAckMessage(fixedHeader, from(messageID));
        sendIfWritableElseDrop(pubAckMessage);
    }

    private void sendPubCompMessage(int messageID) {
        LOGGER.trace("Sending PUBCOMP message on channel: {}, messageId: {}", channel, messageID);
        MqttFixedHeader fixedHeader = new MqttFixedHeader(MqttMessageType.PUBCOMP, false, AT_MOST_ONCE, false, 0);
        MqttMessage pubCompMessage = new MqttPubCompMessage(fixedHeader, from(messageID));
        sendIfWritableElseDrop(pubCompMessage);
    }

   public String getClientId() {
        return NettyUtils.clientID(channel);
    }

   public String getUsername() {
        return NettyUtils.userName(channel);
    }

    public void sendPublishRetainedQos0(Topic topic, MqttQoS qos, ByteBuf payload) {
        MqttPublishMessage publishMsg = retainedPublish(topic.toString(), qos, payload);
        sendPublish(publishMsg);
    }

    public void sendPublishRetainedWithPacketId(Topic topic, MqttQoS qos, ByteBuf payload) {
        final int packetId = nextPacketId();
        MqttPublishMessage publishMsg = retainedPublishWithMessageId(topic.toString(), qos, payload, packetId);
        sendPublish(publishMsg);
    }

    private static MqttPublishMessage retainedPublish(String topic, MqttQoS qos, ByteBuf message) {
        return retainedPublishWithMessageId(topic, qos, message, 0);
    }

    private static MqttPublishMessage retainedPublishWithMessageId(String topic, MqttQoS qos, ByteBuf message,
                                                                   int messageId) {
        MqttFixedHeader fixedHeader = new MqttFixedHeader(MqttMessageType.PUBLISH, false, qos, true, 0);
        MqttPublishVariableHeader varHeader = new MqttPublishVariableHeader(topic, messageId);
        return new MqttPublishMessage(fixedHeader, varHeader, message);
    }

    // TODO move this method in Session
    void sendPublishNotRetainedQos0(Topic topic, MqttQoS qos, ByteBuf payload) {
        MqttPublishMessage publishMsg = notRetainedPublish(topic.toString(), qos, payload);
        sendPublish(publishMsg);
    }

    static MqttPublishMessage notRetainedPublish(String topic, MqttQoS qos, ByteBuf message) {
        return notRetainedPublishWithMessageId(topic, qos, message, 0);
    }

    static MqttPublishMessage notRetainedPublishWithMessageId(String topic, MqttQoS qos, ByteBuf message,
                                                              int messageId) {
        MqttFixedHeader fixedHeader = new MqttFixedHeader(MqttMessageType.PUBLISH, false, qos, false, 0);
        MqttPublishVariableHeader varHeader = new MqttPublishVariableHeader(topic, messageId);
        return new MqttPublishMessage(fixedHeader, varHeader, message);
    }

    public void resendNotAckedPublishes() {
        boundSession.resendInflictNotAcked();
    }

    public int nextPacketId() {
        if (lastPacketId.incrementAndGet() >= 65535) {
            lastPacketId.set(1);
        }
        return lastPacketId.get();
    }


    @Override
    public String toString() {
        return "MQTTConnection{channel=" + channel + ", connected=" + connected + '}';
    }

    InetSocketAddress remoteAddress() {
        return (InetSocketAddress) channel.remoteAddress();
    }

    public void readCompleted() {
        LOGGER.debug("readCompleted client CId: {}, channel: {}", getClientId(), channel);
        if (getClientId() != null) {
            // TODO drain all messages in target's session in-flight message queue
            boundSession.flushAllQueuedMessages();
        }
    }

    @SuppressWarnings("ALL")
    public void clossConnection() {
        channel.close().addListener((ChannelFutureListener) future -> LOGGER.info("Closed client channel due to exception in processing"));
    }

    public void flush() {
        channel.flush();
    }

}
