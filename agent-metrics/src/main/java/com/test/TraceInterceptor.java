package com.test;

import com.test.track.Span;
import com.test.track.TrackManager;
import io.handler.codec.mqtt.MqttCustomerMessage;
import io.handler.codec.mqtt.MqttMessage;
import io.handler.codec.mqtt.MqttPublishMessage;
import io.netty.util.internal.ObjectUtil;
import net.bytebuddy.implementation.bind.annotation.AllArguments;
import net.bytebuddy.implementation.bind.annotation.Origin;
import net.bytebuddy.implementation.bind.annotation.RuntimeType;
import net.bytebuddy.implementation.bind.annotation.SuperCall;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.concurrent.Callable;

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
                if (message.getMqttMessageTranceId() > 0) {
                    Span span = TrackManager.getSpan();
                    if (null == span) {
                        span = new Span(message.getMqttMessageTranceId(), method);
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
