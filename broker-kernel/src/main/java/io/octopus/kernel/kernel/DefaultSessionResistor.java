package io.octopus.kernel.kernel;

import io.octopus.kernel.exception.SessionCorruptedException;
import io.octopus.config.IConfig;
import io.octopus.kernel.kernel.message.KernelPayloadMessage;
import io.octopus.kernel.kernel.queue.Index;
import io.octopus.kernel.kernel.queue.MsgRepository;
import io.octopus.kernel.kernel.repository.IQueueRepository;
import io.octopus.kernel.kernel.security.IRWController;
import io.octopus.kernel.kernel.subscriptions.Topic;
import io.octopus.kernel.utils.ObjectUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.*;

/**
 * @author chenxu
 * @version 1
 * @date 2022/7/5 08:44
 */
public class DefaultSessionResistor implements ISessionResistor {

    Logger logger = LoggerFactory.getLogger(this.getClass());

    private final IQueueRepository queueRepository;
    private final IRWController authorizator;
    private IConfig config;


    private final ConcurrentHashMap<String, DefaultSession> sessions = new ConcurrentHashMap<>();

    private final ConcurrentHashMap<String, Set<String>> usernamePools = new ConcurrentHashMap<>();

    /**
     * 专门用来存储qos1 消息索引的
     */
    private final ConcurrentHashMap<String, Queue<Index>> qos1IndexQueues = new ConcurrentHashMap<>();

    /**
     * 专门用来存储qos2 消息索引的
     */
    private final ConcurrentHashMap<String, Queue<Index>> qos2IndexQueues = new ConcurrentHashMap<>();
    private IPostOffice postOffice = null;

    private final MsgRepository<KernelPayloadMessage> msgRepository;


    public DefaultSessionResistor(IQueueRepository queueRepository, IRWController authorizator,
                                  IConfig config, MsgRepository<KernelPayloadMessage> msgRepository) {
        this.queueRepository = queueRepository;
        this.authorizator = authorizator;
        this.config = config;
        this.msgRepository = msgRepository;
    }

    public void setPostOffice(IPostOffice postOffice) {
        this.postOffice = postOffice;
    }

    @Override
    public SessionCreationResult createOrReOpenSession(String clientId, String username, Boolean isClean, KernelPayloadMessage willMsg, int clientVersion) {

        SessionCreationResult createResult;
        DefaultSession newSession = createNewSession(clientId, username, isClean, willMsg, clientVersion);
        if (!sessions.contains(clientId)) {
            createResult = new SessionCreationResult(newSession, CreationModeEnum.CREATED_CLEAN_NEW, false);

            DefaultSession previous = sessions.putIfAbsent(clientId, newSession);
            Boolean success = previous == null;
            // new session
            if (success) {
                logger.trace("case 1, not existing session with CId {}", clientId);
            } else { //old session
                createResult = reOpenExistingSession(clientId, newSession, username, isClean, willMsg);
            }
        } else {
            createResult = reOpenExistingSession(clientId, newSession, username, isClean, willMsg);
        }
        return createResult;
    }


    public SessionCreationResult reOpenExistingSession(String clientId, DefaultSession newSession,
                                                       String username, Boolean newClean, KernelPayloadMessage willMsg) {
        DefaultSession oldSession = sessions.get(clientId);
        SessionCreationResult result = null;
        if (oldSession.disconnected()) {
            if (newClean) {
                Boolean updatedStatus = oldSession.assignState(SessionStatus.DISCONNECTED, SessionStatus.CONNECTING);
                if (!updatedStatus) {
                    throw new SessionCorruptedException("old session was already changed state");
                }
                // case 2
                // publish new session
                dropQueuesForClient(clientId);
                unsubscribe(oldSession);
                copySessionConfig(newClean, willMsg, oldSession);
                logger.trace("case 2, oldSession with same CId {} disconnected", clientId);
                result = new SessionCreationResult(oldSession, CreationModeEnum.CREATED_CLEAN_NEW, true);
            } else {
                Boolean connecting = oldSession.assignState(SessionStatus.DISCONNECTED, SessionStatus.CONNECTING);
                if (!connecting) {
                    throw new SessionCorruptedException("old session moved in connected state by other thread");
                }
                // case 3
                reactivateSubscriptions(oldSession, username);

                logger.trace("case 3, oldSession with same CId {} disconnected", clientId);
                result = new SessionCreationResult(oldSession, CreationModeEnum.REOPEN_EXISTING, true);
            }
        } else {
            // case 4
            logger.trace("case 4, oldSession with same CId {} still connected, force to close", clientId);
            oldSession.closeImmediately();
            //remove(clientId);
            result = new SessionCreationResult(newSession, CreationModeEnum.DROP_EXISTING, true);
        }
        if (!newClean) { //把消息分发给新的session
            newSession.addQos1InflictWindow(oldSession.getQos1InflictWindow());
        }
        var published = false;
        if (result.mode() != CreationModeEnum.REOPEN_EXISTING) {
            logger.debug("Drop session of already connected client with same id");
            published = sessions.replace(clientId, oldSession, newSession);
        } else {
            logger.debug("Replace session of client with same id");
            published = sessions.replace(clientId, oldSession, oldSession);
        }
        if (!published) {
            throw new SessionCorruptedException("old session was already removed");
        }

        return result;
    }


    /**
     * create new session
     *
     * @param clientId client
     * @return session
     */
    public DefaultSession createNewSession(String clientId, String username, Boolean isClean, KernelPayloadMessage willMsg, int clientVersion) {
        Queue<Index> qos1Queue = qos1IndexQueues.computeIfAbsent(clientId, key -> queueRepository.createQueue(clientId, isClean));
        Queue<Index> qos2Queue = qos2IndexQueues.computeIfAbsent(clientId, key -> queueRepository.createQueue(clientId, isClean));
        Integer receiveMaximum = 10;
        DefaultSession newSession = new DefaultSession(postOffice, clientId, username, isClean,
                willMsg, qos1Queue, qos2Queue, receiveMaximum, clientVersion, msgRepository);
        newSession.markConnecting();
        return newSession;
    }

    /**
     * drop client index queue
     *
     * @param clientId client
     */
    private void dropQueuesForClient(String clientId) {
        qos1IndexQueues.remove(clientId);
    }

    private void reactivateSubscriptions(DefaultSession session, String username) {
        //verify if subscription still satisfy read ACL permissions
        session.getSubTopicList().forEach(topicStr -> {
            boolean topicReadable = authorizator.canRead(new Topic(topicStr), username, session.getClientId());
            if (!topicReadable) {
                postOffice.unSubscriptions(session, session.getSubTopicList());
            }
        });
    }


    /**
     * @param session instance of session {@link DefaultSession}
     */
    private void unsubscribe(ISession session) {
        postOffice.unSubscriptions(session, session.getSubTopicList());
        //    session.getSubTopicList.forEach(existingSub => subscriptionsDirectory.removeSubscription(new Topic(existingSub), session.getClientId))
    }

    /**
     * copy session config (isClean,will)
     *
     * @param isClean     isclean
     * @param willMessage willMsg
     * @param session     session
     */
    private void copySessionConfig(Boolean isClean, KernelPayloadMessage willMessage, DefaultSession session) {
        session.update(isClean, willMessage);
    }


    @Override
    public ISession retrieve(String clientId) {
        return sessions.get(clientId);
    }

    @Override
    public void registerUserName(String username, String clientId) {
        Set<String> pool = usernamePools.computeIfAbsent(clientId, value -> new HashSet<>());
        pool.add(clientId);
    }

    @Override
    public void unRegisterClientIdByUsername(String username, String clientId) {
        Set<String> userClient = usernamePools.get(username);
        if (ObjectUtils.isEmpty(userClient)) {
            return;
        }
        try {
            userClient.remove(clientId);
        } catch (Exception e) {
            e.printStackTrace();

        }
        if (userClient.size() == 0) {
            usernamePools.remove(username);
        }
    }

    @Override
    public Set<String> getClientIdByUsername(String username) {
        return usernamePools.get(username);
    }

    @Override
    public Set<String> getAllClientId() {
        return sessions.keySet();
    }

    @Override
    public void remove(ISession session) {
        sessions.remove(session.getClientId(), session);
        queueRepository.cleanQueue(session.getClientId());
    }


}
