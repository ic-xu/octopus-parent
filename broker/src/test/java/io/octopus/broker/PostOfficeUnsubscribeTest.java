package io.octopus.broker;

import io.octopus.broker.config.BrokerConfiguration;
import io.octopus.broker.security.Authorizator;
import io.octopus.broker.security.PermitAllAuthorizatorPolicy;
import io.octopus.broker.subscriptions.maptree.TopicMapSubscriptionDirectory;
import io.octopus.broker.subscriptions.ISubscriptionsDirectory;
import io.octopus.broker.subscriptions.Subscription;
import io.octopus.broker.subscriptions.Topic;
import io.octopus.broker.security.IAuthenticator;
import io.octopus.persistence.ISubscriptionsRepository;
import io.octopus.persistence.memory.MemoryQueueRepository;
import io.octopus.persistence.memory.MemoryRetainedRepository;
import io.octopus.persistence.memory.MemorySubscriptionsRepository;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.embedded.EmbeddedChannel;
import io.handler.codec.mqtt.*;
import org.junit.Before;
import org.junit.Test;

import java.nio.charset.Charset;
import java.util.Collections;
import java.util.Set;

import static io.octopus.broker.PostOfficePublishTest.PUBLISHER_ID;
import static io.handler.codec.mqtt.MqttQoS.*;
import static java.util.Collections.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class PostOfficeUnsubscribeTest {

    private static final String FAKE_CLIENT_ID = "FAKE_123";
    private static final String TEST_USER = "fakeuser";
    private static final String TEST_PWD = "fakepwd";
    static final String NEWS_TOPIC = "/news";
    private static final String BAD_FORMATTED_TOPIC = "#MQTTClient";

    private MqttConnection connection;
    private EmbeddedChannel channel;
    private PostOffice sut;
    private ISubscriptionsDirectory subscriptions;
    private MqttConnectMessage connectMessage;
    private IAuthenticator mockAuthenticator;
    private SessionRegistry sessionRegistry;
    public static final BrokerConfiguration CONFIG = new BrokerConfiguration(true, true, false, false);
    private MemoryQueueRepository queueRepository;

    @Before
    public void setUp() {
        connectMessage = MqttMessageBuilders.connect()
            .clientId(FAKE_CLIENT_ID)
            .build();

        prepareSUT();
        createMQTTConnection(CONFIG);
    }

    private void createMQTTConnection(BrokerConfiguration config) {
        channel = new EmbeddedChannel();
        connection = createMQTTConnection(config, channel);
    }

    private void prepareSUT() {
        mockAuthenticator = new MockAuthenticator(singleton(FAKE_CLIENT_ID), singletonMap(TEST_USER, TEST_PWD));

        subscriptions = new TopicMapSubscriptionDirectory();
        ISubscriptionsRepository subscriptionsRepository = new MemorySubscriptionsRepository();
        subscriptions.init(subscriptionsRepository);
        queueRepository = new MemoryQueueRepository();

        final PermitAllAuthorizatorPolicy authorizatorPolicy = new PermitAllAuthorizatorPolicy();
        final Authorizator permitAll = new Authorizator(authorizatorPolicy);
        sessionRegistry = new SessionRegistry(subscriptions, queueRepository, permitAll);
        sut = new PostOffice(subscriptions, new MemoryRetainedRepository(), sessionRegistry,
                             ConnectionTestUtils.NO_OBSERVERS_INTERCEPTOR, permitAll);
    }

    private MqttConnection createMQTTConnection(BrokerConfiguration config, Channel channel) {
        return new MqttConnection(channel, config, mockAuthenticator, sessionRegistry, sut);
    }

    protected void connect(MqttConnection connection, String clientId) {
        MqttConnectMessage connectMessage = ConnectionTestUtils.buildConnect(clientId);
        connect(connection, connectMessage);
    }

    protected void connect(MqttConnection connection, MqttConnectMessage connectMessage) {
        connection.processConnect(connectMessage);
        ConnectionTestUtils.assertConnectAccepted((EmbeddedChannel) connection.channel);
    }

    protected void subscribe(MqttConnection connection, String topic, MqttQoS desiredQos) {
        EmbeddedChannel channel = (EmbeddedChannel) connection.channel;
        MqttSubscribeMessage subscribe = MqttMessageBuilders.subscribe()
            .addSubscription(desiredQos, topic)
            .messageId(1)
            .build();
        sut.subscribeClientToTopics(subscribe, connection.getClientId(), null, connection);

        MqttSubAckMessage subAck = channel.readOutbound();
        assertEquals(desiredQos.value(), (int) subAck.payload().grantedQoSLevels().get(0));

        final String clientId = connection.getClientId();
        Subscription expectedSubscription = new Subscription(clientId, new Topic(topic), desiredQos);

        final Set<Subscription> matchedSubscriptions = subscriptions.matchQosSharpening(new Topic(topic),false);
        assertEquals(1, matchedSubscriptions.size());
        //assertTrue(matchedSubscriptions.size() >=1);
        final Subscription onlyMatchedSubscription = matchedSubscriptions.iterator().next();
        assertEquals(expectedSubscription, onlyMatchedSubscription);

//        assertTrue(matchedSubscriptions.contains(expectedSubscription));
    }

    @Test
    public void testUnsubscribeWithBadFormattedTopic() {
        connect(this.connection, FAKE_CLIENT_ID);

        // Exercise
        sut.unsubscribe(singletonList(BAD_FORMATTED_TOPIC), connection, 1);

        // Verify
        assertFalse("Unsubscribe with bad topic MUST close drop the connection, (issue 68)", channel.isOpen());
    }

    @Test
    public void testDontNotifyClientSubscribedToTopicAfterDisconnectedAndReconnectOnSameChannel() {
        connect(this.connection, FAKE_CLIENT_ID);
        subscribe(connection, NEWS_TOPIC, AT_MOST_ONCE);

        // publish on /news
        final ByteBuf payload = Unpooled.copiedBuffer("Hello world!", Charset.defaultCharset());
        sut.receivedPublishQos0(new Topic(NEWS_TOPIC), TEST_USER, TEST_PWD,
            MqttMessageBuilders.publish()
                .payload(payload.retainedDuplicate())
                .qos(MqttQoS.AT_MOST_ONCE)
                .retained(false)
                .topicName(NEWS_TOPIC).build());

        ConnectionTestUtils.verifyPublishIsReceived(channel, AT_MOST_ONCE, "Hello world!");

        unsubscribeAndVerifyAck(NEWS_TOPIC);

        sut.receivedPublishQos0(new Topic(NEWS_TOPIC), TEST_USER, TEST_PWD,
            MqttMessageBuilders.publish()
                .payload(payload)
                .qos(MqttQoS.AT_MOST_ONCE)
                .retained(false)
                .topicName(NEWS_TOPIC).build());

        ConnectionTestUtils.verifyNoPublishIsReceived(channel);
    }

    protected void unsubscribeAndVerifyAck(String topic) {
        final int messageId = 1;

        sut.unsubscribe(Collections.singletonList(topic), connection, messageId);

        MqttUnsubAckMessage unsubAckMessageAck = channel.readOutbound();
        assertEquals("Unsubscribe must be accepted", messageId, unsubAckMessageAck.variableHeader().messageId());
    }

    @Test
    public void testDontNotifyClientSubscribedToTopicAfterDisconnectedAndReconnectOnNewChannel() {
        connect(this.connection, FAKE_CLIENT_ID);
        subscribe(connection, NEWS_TOPIC, AT_MOST_ONCE);
        // publish on /news
        final ByteBuf payload = Unpooled.copiedBuffer("Hello world!", Charset.defaultCharset());
        sut.receivedPublishQos0(new Topic(NEWS_TOPIC), TEST_USER, TEST_PWD,
            MqttMessageBuilders.publish()
                .payload(payload.retainedDuplicate())
                .qos(MqttQoS.AT_MOST_ONCE)
                .retained(false)
                .topicName(NEWS_TOPIC).build());

        ConnectionTestUtils.verifyPublishIsReceived(channel, AT_MOST_ONCE, "Hello world!");

        unsubscribeAndVerifyAck(NEWS_TOPIC);
        connection.processDisconnect(null);

        // connect on another channel
        EmbeddedChannel anotherChannel = new EmbeddedChannel();
        MqttConnection anotherConnection = createMQTTConnection(CONFIG, anotherChannel);
        anotherConnection.processConnect(connectMessage);
        ConnectionTestUtils.assertConnectAccepted(anotherChannel);

        // publish on /news
        final ByteBuf payload2 = Unpooled.copiedBuffer("Hello world!", Charset.defaultCharset());
        sut.receivedPublishQos0(new Topic(NEWS_TOPIC), TEST_USER, TEST_PWD,
            MqttMessageBuilders.publish()
                .payload(payload2)
                .qos(MqttQoS.AT_MOST_ONCE)
                .retained(false)
                .topicName(NEWS_TOPIC).build());

        ConnectionTestUtils.verifyNoPublishIsReceived(anotherChannel);
    }

    @Test
    public void avoidMultipleNotificationsAfterMultipleReconnection_cleanSessionFalseQoS1() {
        final MqttConnectMessage notCleanConnect = ConnectionTestUtils.buildConnectNotClean(FAKE_CLIENT_ID);
        connect(connection, notCleanConnect);
        subscribe(connection, NEWS_TOPIC, AT_LEAST_ONCE);
        connection.processDisconnect(null);

        // connect on another channel
        final String firstPayload = "Hello MQTT 1";
        connectPublishDisconnectFromAnotherClient(firstPayload, NEWS_TOPIC);

        // reconnect FAKE_CLIENT on another channel
        EmbeddedChannel anotherChannel2 = new EmbeddedChannel();
        MqttConnection anotherConnection2 = createMQTTConnection(CONFIG, anotherChannel2);
        anotherConnection2.processConnect(notCleanConnect);
        ConnectionTestUtils.assertConnectAccepted(anotherChannel2);

        ConnectionTestUtils.verifyPublishIsReceived(anotherChannel2, MqttQoS.AT_LEAST_ONCE, firstPayload);

        anotherConnection2.processDisconnect(null);

        final String secondPayload = "Hello MQTT 2";
        connectPublishDisconnectFromAnotherClient(secondPayload, NEWS_TOPIC);

        EmbeddedChannel anotherChannel3 = new EmbeddedChannel();
        MqttConnection anotherConnection3 = createMQTTConnection(CONFIG, anotherChannel3);
        anotherConnection3.processConnect(notCleanConnect);
        ConnectionTestUtils.assertConnectAccepted(anotherChannel3);

        ConnectionTestUtils.verifyPublishIsReceived(anotherChannel3, MqttQoS.AT_LEAST_ONCE, secondPayload);
    }

    private void connectPublishDisconnectFromAnotherClient(String firstPayload, String topic) {
        MqttConnection anotherConnection = connectNotCleanAs(PUBLISHER_ID);

        // publish from another channel
        final ByteBuf anyPayload = Unpooled.copiedBuffer(firstPayload, Charset.defaultCharset());
        sut.receivedPublishQos1(anotherConnection, new Topic(topic), TEST_USER, 1,
            MqttMessageBuilders.publish()
                .payload(Unpooled.copiedBuffer(firstPayload, Charset.defaultCharset()))
                .qos(MqttQoS.AT_LEAST_ONCE)
                .retained(false)
                .topicName(topic).build());

        // disconnect the other channel
        anotherConnection.processDisconnect(null);
    }

    private MqttConnection connectNotCleanAs(String clientId) {
        EmbeddedChannel channel = new EmbeddedChannel();
        MqttConnection connection = createMQTTConnection(CONFIG, channel);
        connection.processConnect(ConnectionTestUtils.buildConnectNotClean(clientId));
        ConnectionTestUtils.assertConnectAccepted(channel);
        return connection;
    }

    private MqttConnection connectAs(String clientId) {
        EmbeddedChannel channel = new EmbeddedChannel();
        MqttConnection connection = createMQTTConnection(CONFIG, channel);
        connection.processConnect(ConnectionTestUtils.buildConnect(clientId));
        ConnectionTestUtils.assertConnectAccepted(channel);
        return connection;
    }

    @Test
    public void testConnectSubPub_cycle_getTimeout_on_second_disconnect_issue142() {
        connect(connection, FAKE_CLIENT_ID);
        subscribe(connection, NEWS_TOPIC, AT_MOST_ONCE);
        // publish on /news
        final ByteBuf payload = Unpooled.copiedBuffer("Hello world!", Charset.defaultCharset());
        sut.receivedPublishQos0(new Topic(NEWS_TOPIC), TEST_USER, TEST_PWD,
            MqttMessageBuilders.publish()
                .payload(payload.retainedDuplicate())
                .qos(MqttQoS.AT_MOST_ONCE)
                .retained(false)
                .topicName(NEWS_TOPIC).build());

        ConnectionTestUtils.verifyPublishIsReceived((EmbeddedChannel) connection.channel, AT_MOST_ONCE, "Hello world!");

        connection.processDisconnect(null);

        final MqttConnectMessage notCleanConnect = ConnectionTestUtils.buildConnect(FAKE_CLIENT_ID);
        EmbeddedChannel subscriberChannel = new EmbeddedChannel();
        MqttConnection subscriberConnection = createMQTTConnection(CONFIG, subscriberChannel);
        subscriberConnection.processConnect(notCleanConnect);
        ConnectionTestUtils.assertConnectAccepted(subscriberChannel);

        subscribe(subscriberConnection, NEWS_TOPIC, AT_MOST_ONCE);
        // publish on /news
        final ByteBuf payload2 = Unpooled.copiedBuffer("Hello world2!", Charset.defaultCharset());
        sut.receivedPublishQos0(new Topic(NEWS_TOPIC), TEST_USER, TEST_PWD,
            MqttMessageBuilders.publish()
                .payload(payload2.retainedDuplicate())
                .qos(MqttQoS.AT_MOST_ONCE)
                .retained(false)
                .topicName(NEWS_TOPIC).build());

        ConnectionTestUtils.verifyPublishIsReceived(subscriberChannel, AT_MOST_ONCE, "Hello world2!");

        subscriberConnection.processDisconnect(null);

        assertFalse("after a disconnect the client should be disconnected", subscriberChannel.isOpen());
    }

    @Test
    public void checkReplayofStoredPublishResumeAfter_a_disconnect_cleanSessionFalseQoS1() {
        final MqttConnection publisher = connectAs("Publisher");

        connect(this.connection, FAKE_CLIENT_ID);
        subscribe(connection, NEWS_TOPIC, AT_LEAST_ONCE);

        // publish from another channel
        publishQos1(publisher, NEWS_TOPIC, "Hello world MQTT!!-1", 99);
        ConnectionTestUtils.verifyPublishIsReceived(channel, AT_LEAST_ONCE, "Hello world MQTT!!-1");
        connection.processDisconnect(null);

        publishQos1(publisher, NEWS_TOPIC, "Hello world MQTT!!-2", 100);
        publishQos1(publisher, NEWS_TOPIC, "Hello world MQTT!!-3", 101);

        createMQTTConnection(CONFIG);
        connect(this.connection, FAKE_CLIENT_ID);
        ConnectionTestUtils.verifyPublishIsReceived(channel, AT_LEAST_ONCE, "Hello world MQTT!!-2");
        ConnectionTestUtils.verifyPublishIsReceived(channel, AT_LEAST_ONCE, "Hello world MQTT!!-3");
    }

    private void publishQos1(MqttConnection publisher, String topic, String payload, int messageID) {
        final ByteBuf bytePayload = Unpooled.copiedBuffer(payload, Charset.defaultCharset());
        sut.receivedPublishQos1(publisher, new Topic(topic), TEST_USER, messageID,
            MqttMessageBuilders.publish()
                .payload(Unpooled.copiedBuffer(payload, Charset.defaultCharset()))
                .qos(MqttQoS.AT_LEAST_ONCE)
                .retained(false)
                .topicName(NEWS_TOPIC).build());
    }

    private void publishQos2(MqttConnection connection, String topic, String payload) {
        final ByteBuf bytePayload = Unpooled.copiedBuffer(payload, Charset.defaultCharset());
        sut.receivedPublishQos2(connection, MqttMessageBuilders.publish()
            .payload(bytePayload)
            .qos(MqttQoS.EXACTLY_ONCE)
            .retained(true)
            .topicName(NEWS_TOPIC).build(), "username");
    }

    /**
     * subscriber connect and subscribe on "topic" subscriber disconnects publisher connects and
     * send two message "hello1" "hello2" to "topic" subscriber connects again and receive "hello1"
     * "hello2"
     */
    @Test
    public void checkQoS2SubscriberDisconnectReceivePersistedPublishes() {
        connect(this.connection, FAKE_CLIENT_ID);
        subscribe(connection, NEWS_TOPIC, EXACTLY_ONCE);
        connection.processDisconnect(null);

        final MqttConnection publisher = connectAs("Publisher");
        publishQos2(publisher, NEWS_TOPIC, "Hello world MQTT!!-1");
        publishQos2(publisher, NEWS_TOPIC, "Hello world MQTT!!-2");

        createMQTTConnection(CONFIG);
        connect(this.connection, FAKE_CLIENT_ID);
        ConnectionTestUtils.verifyPublishIsReceived(channel, EXACTLY_ONCE, "Hello world MQTT!!-1");
        ConnectionTestUtils.verifyPublishIsReceived(channel, EXACTLY_ONCE, "Hello world MQTT!!-2");
    }

    /**
     * subscriber connect and subscribe on "a/b" QoS 1 and "a/+" QoS 2 publisher connects and send a
     * message "hello" on "a/b" subscriber must receive only a single message not twice
     */
    @Test
    public void checkSinglePublishOnOverlappingSubscriptions() {
        final MqttConnection publisher = connectAs("Publisher");

        connect(this.connection, FAKE_CLIENT_ID);
        subscribe(connection, "a/b", AT_LEAST_ONCE);
        subscribe(connection, "a/+", EXACTLY_ONCE);

        // force the publisher to send
        publishQos1(publisher, "a/b", "Hello world MQTT!!", 60);

        ConnectionTestUtils.verifyPublishIsReceived(channel, AT_LEAST_ONCE, "Hello world MQTT!!");
        ConnectionTestUtils.verifyNoPublishIsReceived(channel);
    }
}
