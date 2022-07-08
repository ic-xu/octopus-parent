package com.test;

import com.test.track.Span;
import com.test.track.TrackManager;
import io.handler.codec.mqtt.MqttCustomerMessage;
import io.handler.codec.mqtt.MqttMessage;
import io.handler.codec.mqtt.MqttPublishMessage;
import io.octopus.kernel.kernel.message.IMessage;
import io.octopus.kernel.kernel.queue.StoreMsg;
import net.bytebuddy.implementation.bind.annotation.AllArguments;
import net.bytebuddy.implementation.bind.annotation.Origin;
import net.bytebuddy.implementation.bind.annotation.RuntimeType;
import net.bytebuddy.implementation.bind.annotation.SuperCall;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.concurrent.Callable;

/**
 * @author user
 */
public class TraceInterceptor {

    static Logger logger = LoggerFactory.getLogger(TraceInterceptor.class);

    @RuntimeType
    public static Object interceptor(@Origin Method method, @AllArguments Object[] allArguments, @SuperCall Callable<?> callable) throws Exception {

        if (allArguments.length < 1) {
            return callable.call();
        }

        for (Object arg : allArguments) {
            if (arg instanceof MqttPublishMessage || arg instanceof MqttCustomerMessage) {
                MqttMessage message = (MqttMessage) arg;
                    Span span = TrackManager.getSpan();
                    if (null == span) {
                        span = new Span(message.longId(), method);
                        TrackManager.setSpan(span);
                    }
                    try {
                        span.increment();
                        return callable.call();
                    } catch (Exception e) {
                        e.printStackTrace();
                    } finally {
                        logger.info("{\"messageId\":\"{}\",\"spanId\":{}, \"method\":\"{}\",\"startTime:\":\"{}\",\"timestamp\":{},\"castTime\": {} }",
                                span.getTrackId(),
                                span.getSpanId(),
                                method.getDeclaringClass().getTypeName() + "." + method.getName(),
                                span.getStartTime(),
                                System.currentTimeMillis(),
                                (System.currentTimeMillis() - span.getStartTime()));
                        span.decrement();
                        if (span.getMethod().equals(method)) {
                            TrackManager.clear();
                        }
                    }


            }
            else if(arg instanceof StoreMsg){
                MqttMessage message = (MqttMessage) ((StoreMsg<IMessage>) arg).getMsg();
                if (message.longId() > 0) {
                    Span span = TrackManager.getSpan();
                    if (null == span) {
                        span = new Span(message.longId(), method);
                        TrackManager.setSpan(span);
                    }
                    try {
                        span.increment();
                        return callable.call();
                    } catch (Exception e) {
                        e.printStackTrace();
                    } finally {
                        logger.info("{\"messageId\":\"{}\",\"spanId\":{}, \"method\":\"{}\", \"timestamp\":{},\"castTime\": {} }", span.getTrackId()
                                , span.getSpanId(), method.getDeclaringClass().getTypeName() + "." + method.getName(), span.getStartTime(),
                                (System.currentTimeMillis() - span.getStartTime()));
                        span.decrement();
                        if (span.getMethod().equals(method)) {
                            TrackManager.clear();
                        }
                    }
                }
            }
        }
        return callable.call();


    }
}
