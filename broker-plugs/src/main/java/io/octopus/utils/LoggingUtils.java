package io.octopus.utils;

import io.octopus.kernel.kernel.interceptor.PostOfficeNotifyInterceptor;

import java.util.ArrayList;
import java.util.Collection;

public final class LoggingUtils {

    public static <T extends PostOfficeNotifyInterceptor> Collection<String> getInterceptorIds(Collection<T> handlers) {
        Collection<String> result = new ArrayList<>(handlers.size());
        for (T handler : handlers) {
            result.add(handler.getIdentity());
        }
        return result;
    }

    private LoggingUtils() {
    }
}
