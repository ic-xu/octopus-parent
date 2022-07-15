package io.octopus.kernel.kernel.metrics;

import com.codahale.metrics.*;
import io.octopus.kernel.kernel.IPostOffice;

import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.util.Collections;
import java.util.Set;
import java.util.SortedMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * @author user
 */
public class MetricsPubMessageReport extends ScheduledReporter {
    private IPostOffice msgDispatcher;
    private OperatingSystemMXBean systemMXBean ;
    private static final Runtime RUNTIME = Runtime.getRuntime();


    protected MetricsPubMessageReport(MetricRegistry registry, String name, MetricFilter filter,
                                      TimeUnit rateUnit, TimeUnit durationUnit,
                                      ScheduledExecutorService executor, boolean shutdownExecutorOnStop,
                                      Set<MetricAttribute> disabledMetricAttributes, IPostOffice msgDispatcher) {
        super(registry, name, filter, rateUnit, durationUnit, executor, shutdownExecutorOnStop, disabledMetricAttributes);
        this.msgDispatcher = msgDispatcher;
        this.systemMXBean = ManagementFactory.getOperatingSystemMXBean();
    }


    @Override
    public void report(SortedMap<String, Gauge> gauges,
                       SortedMap<String, Counter> counters,
                       SortedMap<String, Histogram> histograms,
                       SortedMap<String, Meter> meters,
                       SortedMap<String, Timer> timers) {

    }





    public static MetricsPubMessageReport.Builder forRegistry(MetricRegistry registry) {
        return new MetricsPubMessageReport.Builder(registry);
    }

    final public static class Builder {
        private final MetricRegistry registry;
        private TimeUnit rateUnit;
        private TimeUnit durationUnit;
        private MetricFilter filter;
        private String name;
        private ScheduledExecutorService executor;
        private boolean shutdownExecutorOnStop;
        private Set<MetricAttribute> disabledMetricAttributes;

        private Builder(MetricRegistry registry) {
            this.registry = registry;
            this.rateUnit = TimeUnit.SECONDS;
            this.durationUnit = TimeUnit.MILLISECONDS;
            this.filter = MetricFilter.ALL;
            this.executor = null;
            this.name = "MetricsPubMessageReport";
            this.shutdownExecutorOnStop = true;
            this.disabledMetricAttributes = Collections.emptySet();
        }

        public MetricsPubMessageReport.Builder shutdownExecutorOnStop(boolean shutdownExecutorOnStop) {
            this.shutdownExecutorOnStop = shutdownExecutorOnStop;
            return this;
        }

        public MetricsPubMessageReport.Builder scheduleOn(ScheduledExecutorService executor) {
            this.executor = executor;
            return this;
        }


        public MetricsPubMessageReport.Builder convertRatesTo(TimeUnit rateUnit) {
            this.rateUnit = rateUnit;
            return this;
        }

        public MetricsPubMessageReport.Builder convertDurationsTo(TimeUnit durationUnit) {
            this.durationUnit = durationUnit;
            return this;
        }

        public MetricsPubMessageReport.Builder filter(MetricFilter filter) {
            this.filter = filter;
            return this;
        }

        public MetricsPubMessageReport.Builder reName(String name) {
            this.name = name;
            return this;
        }

        public MetricsPubMessageReport.Builder disabledMetricAttributes(Set<MetricAttribute> disabledMetricAttributes) {
            this.disabledMetricAttributes = disabledMetricAttributes;
            return this;
        }

        public MetricsPubMessageReport build(IPostOffice msgDispatcher) {
            return new MetricsPubMessageReport(this.registry, this.name, this.filter, this.rateUnit, this.durationUnit, this.executor, this.shutdownExecutorOnStop, this.disabledMetricAttributes, msgDispatcher);
        }
    }
}
