package io.octopus.broker.subscriptions.maptree;

import io.handler.codec.mqtt.MqttQoS;
import io.octopus.broker.subscriptions.Subscription;
import io.octopus.broker.subscriptions.Topic;
import io.octopus.broker.subscriptions.TopicRegister;
import io.octopus.utils.TopicUtils;
import junit.framework.TestCase;

import java.util.Set;

public class MemoryTopicRegisterTest extends TestCase {

    public void testRegisterTopic() {
    }

    public void testUnRegisterTopic() {
    }

    public void testGetSubscriptions() {
    }


    public static void main(String[] args) {

        TopicRegister topicRegister = new MemoryTopicRegister("root");

        register("+/+/b", topicRegister, new Subscription("1", new Topic("1"), MqttQoS.AT_MOST_ONCE));

        Set<Subscription> subscriptions = topicRegister.getSubscriptions(TopicUtils.getTopicLevelArr("sport/tennis/b"));
        System.err.println(subscriptions);
        topicRegister.unRegisterTopic(new Subscription("1",new Topic("1"),MqttQoS.AT_LEAST_ONCE),"#");
        System.err.println(topicRegister.toString());

//        topicTreeMap.unRegisterTopic(new Subscription("" + 0, new Topic(0 + ""), MqttQoS.AT_MOST_ONCE), "a/b/c/#".split("/"));
//        System.err.println(topicTreeMap.toString());
    }

    public static void register(String sss, TopicRegister topicRegister, Subscription subscription) {
        topicRegister.registerTopic(subscription, TopicUtils.getTopicLevelArr(sss));
    }
}