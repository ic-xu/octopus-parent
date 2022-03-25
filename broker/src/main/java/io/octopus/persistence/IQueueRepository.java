package io.octopus.persistence;

import io.octopus.broker.SessionRegistry;

import java.util.Queue;

public interface IQueueRepository {

    Queue<SessionRegistry.EnqueuedMessage> createQueue(String cli, boolean clean);
}
