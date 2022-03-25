package io.octopus.broker;

import io.octopus.broker.Session.SessionStatus;
import io.octopus.broker.exception.SessionCorruptedException;
import io.octopus.broker.security.Authorizator;
import io.octopus.broker.subscriptions.ISubscriptionsDirectory;
import io.octopus.broker.subscriptions.Subscription;
import io.octopus.broker.subscriptions.Topic;
import io.octopus.persistence.IQueueRepository;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.handler.codec.mqtt.MqttConnectMessage;
import io.handler.codec.mqtt.MqttQoS;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

public class SessionRegistry {


    private Channel udpChannel;

    public Channel getUdpChannel() {
        return udpChannel;
    }

    public void setUdpChannel(Channel udpChannel) {
        this.udpChannel = udpChannel;
    }

    public abstract static class EnqueuedMessage {
        /**
         * Releases any held resources. Must be called when the EnqueuedMessage is no
         * longer needed.
         */
        public void release() {}

        /**
         * Retains any held resources. Must be called when the EnqueuedMessage is added
         * to a store.
         */
        public void retain() {}
    }

    static class PublishedMessage extends EnqueuedMessage {

        final Topic topic;
        final MqttQoS publishingQos;
        final ByteBuf payload;

        PublishedMessage(Topic topic, MqttQoS publishingQos, ByteBuf payload) {
            this.topic = topic;
            this.publishingQos = publishingQos;
            this.payload = payload;
        }

        @Override
        public void release() {
            this.payload.release();
        }

        @Override
        public void retain() {
            this.payload.retain();
        }
    }

    static final class PubRelMarker extends EnqueuedMessage {

    }

    public enum CreationModeEnum {
        CREATED_CLEAN_NEW, REOPEN_EXISTING, DROP_EXISTING
    }

    public static class SessionCreationResult {

        final Session session;
        final CreationModeEnum mode;
        final boolean alreadyStored;

        public SessionCreationResult(Session session, CreationModeEnum mode, boolean alreadyStored) {
            this.session = session;
            this.mode = mode;
            this.alreadyStored = alreadyStored;
        }
    }

    private static final Logger LOG = LoggerFactory.getLogger(SessionRegistry.class);


    private final ISubscriptionsDirectory subscriptionsDirectory;
    private final IQueueRepository queueRepository;
    private final Authorizator authorizator;
    //userName -> clientId
    private final Map<String, Set<String>> usernamePool = new ConcurrentHashMap<>();
    //clientId -> messageQueue
    private final ConcurrentMap<String, Queue<SessionRegistry.EnqueuedMessage>> queues = new ConcurrentHashMap<>();
    //clientId -> session
    private final ConcurrentMap<String, Session> sessions = new ConcurrentHashMap<>();

    SessionRegistry(ISubscriptionsDirectory subscriptionsDirectory,
                    IQueueRepository queueRepository,
                    Authorizator authorizator) {
        this.subscriptionsDirectory = subscriptionsDirectory;
        this.queueRepository = queueRepository;
        this.authorizator = authorizator;
    }


    public void registerUserName(String username, String clientId) {
        Set<String> pool = usernamePool.computeIfAbsent(username, k -> new HashSet<>());
        pool.add(clientId);
    }

    public void removeUserClientIdByUsername(String username,String clientId){
        Set<String> userClient = usernamePool.get(username);
        userClient.remove(clientId);
        if(userClient.size()==0){
            usernamePool.remove(username);
        }
    }

    public Set<String> getClientIdByUsername(String username) {
        return usernamePool.get(username);
    }


    SessionCreationResult createOrReopenSession(MqttConnectMessage msg, String clientId, String username) {
        SessionCreationResult postConnectAction;
        final Session newSession = createNewSession(msg, clientId);
        if (!sessions.containsKey(clientId)) {
            // case 1
            postConnectAction = new SessionCreationResult(newSession, CreationModeEnum.CREATED_CLEAN_NEW, false);

            // publish the session
            final Session previous = sessions.putIfAbsent(clientId, newSession);
            final boolean success = previous == null;
            // new session
            if (success) {
                LOG.trace("case 1, not existing session with CId {}", clientId);
            } else {
                //old session
                postConnectAction = reOpenExistingSession(msg, clientId, newSession, username);
            }
        } else {
            postConnectAction = reOpenExistingSession(msg, clientId, newSession, username);
        }
        return postConnectAction;
    }

    private SessionCreationResult reOpenExistingSession(MqttConnectMessage msg, String clientId,
                                                        Session newSession, String username) {
        final boolean newIsClean = msg.variableHeader().isCleanSession();
        final Session oldSession = sessions.get(clientId);
        final SessionCreationResult creationResult;
        if (oldSession.disconnected()) {
            if (newIsClean) {
                boolean result = oldSession.assignState(SessionStatus.DISCONNECTED, SessionStatus.CONNECTING);
                if (!result) {
                    throw new SessionCorruptedException("old session was already changed state");
                }

                // case 2
                // publish new session
                dropQueuesForClient(clientId);
                unsubscribe(oldSession);
                copySessionConfig(msg, oldSession);

                LOG.trace("case 2, oldSession with same CId {} disconnected", clientId);
                creationResult = new SessionCreationResult(oldSession, CreationModeEnum.CREATED_CLEAN_NEW, true);
            } else {
                final boolean connecting = oldSession.assignState(SessionStatus.DISCONNECTED, SessionStatus.CONNECTING);
                if (!connecting) {
                    throw new SessionCorruptedException("old session moved in connected state by other thread");
                }
                // case 3
                reactivateSubscriptions(oldSession, username);

                LOG.trace("case 3, oldSession with same CId {} disconnected", clientId);
                creationResult = new SessionCreationResult(oldSession, CreationModeEnum.REOPEN_EXISTING, true);
            }
        } else {
            // case 4
            LOG.trace("case 4, oldSession with same CId {} still connected, force to close", clientId);
            oldSession.closeImmediately();
            //remove(clientId);
            creationResult = new SessionCreationResult(newSession, CreationModeEnum.DROP_EXISTING, true);
        }
        if (!msg.variableHeader().isCleanSession())
            //把消息分发给新的session
            newSession.addInflictWindow(oldSession.getInflictWindow());
        final boolean published;
        if (creationResult.mode != CreationModeEnum.REOPEN_EXISTING) {
            LOG.debug("Drop session of already connected client with same id");
            published = sessions.replace(clientId, oldSession, newSession);
        } else {
            LOG.debug("Replace session of client with same id");
            published = sessions.replace(clientId, oldSession, oldSession);
        }
        if (!published) {
            throw new SessionCorruptedException("old session was already removed");
        }

        // case not covered new session is clean true/false and old session not in CONNECTED/DISCONNECTED
        return creationResult;
    }

    private void reactivateSubscriptions(Session session, String username) {
        //verify if subscription still satisfy read ACL permissions
        for (Subscription existingSub : session.getSubscriptions()) {
            final boolean topicReadable = authorizator.canRead(existingSub.getTopicFilter(), username,
                    session.getClientID());
            if (!topicReadable) {
                subscriptionsDirectory.removeSubscription(existingSub.getTopicFilter(), session.getClientID());
            }
            // TODO
//            subscriptionsDirectory.reactivate(existingSub.getTopicFilter(), session.getClientID());
        }
    }

    private void unsubscribe(Session session) {
        for (Subscription existingSub : session.getSubscriptions()) {
            subscriptionsDirectory.removeSubscription(existingSub.getTopicFilter(), session.getClientID());
        }
    }

    private Session createNewSession(MqttConnectMessage msg, String clientId) {
        final boolean clean = msg.variableHeader().isCleanSession();
        final Queue<SessionRegistry.EnqueuedMessage> sessionQueue =
                queues.computeIfAbsent(clientId, (String cli) -> queueRepository.createQueue(cli, clean));
        final Session newSession;
        if (msg.variableHeader().isWillFlag()) {
            final Session.Will will = createWill(msg);
            newSession = new Session(clientId, clean, will, sessionQueue,10,msg.variableHeader().version());
        } else {
            newSession = new Session(clientId, clean, sessionQueue,10,msg.variableHeader().version());
        }

        newSession.markConnecting();
        return newSession;
    }

    private void copySessionConfig(MqttConnectMessage msg, Session session) {
        final boolean clean = msg.variableHeader().isCleanSession();
        final Session.Will will;
        if (msg.variableHeader().isWillFlag()) {
            will = createWill(msg);
        } else {
            will = null;
        }
        session.update(clean, will);
    }

    private Session.Will createWill(MqttConnectMessage msg) {
        final ByteBuf willPayload = Unpooled.copiedBuffer(msg.payload().willMessageInBytes());
        final String willTopic = msg.payload().willTopic();
        final boolean retained = msg.variableHeader().isWillRetain();
        final MqttQoS qos = MqttQoS.valueOf(msg.variableHeader().willQos());
        return new Session.Will(willTopic, willPayload, qos, retained);
    }

   public Session retrieve(String clientID) {
        return sessions.get(clientID);
    }

    /**
     * find all session
     * @return sessions
     */
    public ConcurrentMap<String, Session> getSessionAll(){
        return sessions;
    }


    /**
     * get all session
     * @return sessions
     */
    public Set<String> getAllClientId(){
        return sessions.keySet();
    }

    public void remove(Session session) {
        sessions.remove(session.getClientID(), session);
    }

    private void dropQueuesForClient(String clientId) {
        queues.remove(clientId);
    }

    Collection<ClientDescriptor> listConnectedClients() {
        return sessions.values().stream()
                .filter(Session::connected)
                .map(this::createClientDescriptor)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toList());
    }

    private Optional<ClientDescriptor> createClientDescriptor(Session s) {
        final String clientID = s.getClientID();
        final Optional<InetSocketAddress> remoteAddressOpt = s.remoteAddress();
        return remoteAddressOpt.map(r -> new ClientDescriptor(clientID, r.getHostString(), r.getPort()));
    }
}
