package io.octopus.kernel.kernel.interceptor;

import io.octopus.kernel.kernel.message.KernelMsg;
import io.octopus.kernel.kernel.subscriptions.Subscription;

/**
 * @author chenxu
 * @version 1
 * @date 2022/1/21 5:11 下午
 */
public interface ConnectionNotifyInterceptor {



    void notifyClientConnected(KernelMsg msg);

    void notifyClientDisconnected(String clientId, String username);

    void notifyClientConnectionLost(String clientId, String username);

    void notifyTopicBeforePublished(KernelMsg msg);

    void notifyTopicPublished(KernelMsg msg, String clientId, String username);

    void notifyTopicSubscribed(Subscription sub, String username);

    void notifyTopicUnsubscribed(String topic, String clientId, String username);

    void notifyMessageAcknowledged(KernelMsg msg);

    void addInterceptHandler(KernelMsg interceptHandler);

    void removeInterceptHandler(KernelMsg interceptHandler);
}
