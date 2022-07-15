package io.octopus.kernel.kernel.interceptor;

import io.octopus.kernel.kernel.message.KernelPayloadMessage;
import io.octopus.kernel.kernel.subscriptions.Subscription;

/**
 * @author chenxu
 * @version 1
 * @date 2022/1/21 5:11 下午
 */
public interface PostOfficeNotifyInterceptor {

    String getIdentity();

    void notifyTopicBeforePublished(KernelPayloadMessage msg);

    void notifyTopicPublished(KernelPayloadMessage msg, String clientId, String username);

    void notifyTopicSubscribed(Subscription sub, String username);

    void notifyTopicUnsubscribed(String topic, String clientId, String username);

    void notifyMessageAcknowledged(KernelPayloadMessage msg);

    void addInterceptHandler(KernelPayloadMessage interceptHandler);

    void removeInterceptHandler(KernelPayloadMessage interceptHandler);
}
