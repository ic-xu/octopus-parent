package io.octopus.utils;

import java.util.ArrayList;
import java.util.Collection;
import io.octopus.interception.InterceptHandler;

public final class LoggingUtils {

    public static <T extends InterceptHandler> Collection<String> getInterceptorIds(Collection<T> handlers) {
        Collection<String> result = new ArrayList<>(handlers.size());
        for (T handler : handlers) {
            result.add(handler.getID());
        }
        return result;
    }

    private LoggingUtils() {
    }
}
