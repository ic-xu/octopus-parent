package io.octopus.broker.metrics;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Collects all the metrics from the various pipeline.
 * @author user
 */
public class BytesMetricsCollector {

    private final AtomicLong readBytes = new AtomicLong();
    private final AtomicLong wroteBytes = new AtomicLong();

    public BytesMetrics computeMetrics() {
        BytesMetrics allMetrics = new BytesMetrics();
        allMetrics.incrementRead(readBytes.get());
        allMetrics.incrementWrote(wroteBytes.get());
        return allMetrics;
    }

    public void sumReadBytes(long count) {
        readBytes.getAndAdd(count);
    }

    public void sumWroteBytes(long count) {
        wroteBytes.getAndAdd(count);
    }
}
