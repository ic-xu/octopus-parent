package io.octopus.broker.metrics;

import com.codahale.metrics.*;
import com.google.gson.Gson;
import io.handler.codec.mqtt.*;
import io.netty.buffer.Unpooled;
import io.octopus.broker.PostOffice;
import io.octopus.contants.ConstantsTopics;

import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Set;
import java.util.SortedMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

public class MetricsPubMessageReport extends ScheduledReporter {
    private PostOffice postOffice;
    private OperatingSystemMXBean systemMXBean ;
    private static final Runtime runtime = Runtime.getRuntime();


    protected MetricsPubMessageReport(MetricRegistry registry, String name, MetricFilter filter, TimeUnit rateUnit, TimeUnit durationUnit, ScheduledExecutorService executor, boolean shutdownExecutorOnStop, Set<MetricAttribute> disabledMetricAttributes, PostOffice postOffice) {
        super(registry, name, filter, rateUnit, durationUnit, executor, shutdownExecutorOnStop, disabledMetricAttributes);
        this.postOffice = postOffice;
        this.systemMXBean = ManagementFactory.getOperatingSystemMXBean();
    }


    @Override
    public void report(SortedMap<String, Gauge> gauges,
                       SortedMap<String, Counter> counters,
                       SortedMap<String, Histogram> histograms,
                       SortedMap<String, Meter> meters,
                       SortedMap<String, Timer> timers) {
        MqttPublishMessage mqttPublishMessage = null;
        if (!gauges.isEmpty()) {
            mqttPublishMessage = wrappMessage(gauges);
        }
        if (!counters.isEmpty()) {
            mqttPublishMessage = wrappMessage(counters);
        }

        if (!histograms.isEmpty()) {
            mqttPublishMessage = wrappMessage(histograms);
        }

        if (!meters.isEmpty()) {
            mqttPublishMessage = wrappMessage(meters);
        }

        if (!timers.isEmpty()) {
            mqttPublishMessage = wrappMessage(timers);
        }
        if(null!=mqttPublishMessage){
            postOffice.internalPublish(mqttPublishMessage);
            mqttPublishMessage.payload().release();
        }
    }


    public MqttPublishMessage wrappMessage(SortedMap metricSortedMap) {
        MqttPublishVariableHeader variableHeader = new MqttPublishVariableHeader(ConstantsTopics.$SYS_METRICS, ThreadLocalRandom.current().nextInt(10000));
        Gson gson = new Gson();
        byte[] bytes1 = gson.toJson(variableHeader).getBytes(StandardCharsets.UTF_8);
        byte[] bytes = gson.toJson(metricSortedMap).getBytes(StandardCharsets.UTF_8);
        MqttFixedHeader header = new MqttFixedHeader(MqttMessageType.PUBLISH, false, MqttQoS.AT_MOST_ONCE, false, bytes1.length + bytes.length);
        return new MqttPublishMessage(header, variableHeader, Unpooled.copiedBuffer(bytes));
    }




    private MqttPublishMessage jvmInfo(){
        return null;
    }


    public static MetricsPubMessageReport.Builder forRegistry(MetricRegistry registry) {
        return new MetricsPubMessageReport.Builder(registry);
    }

    public static class Builder {
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

        public MetricsPubMessageReport build(PostOffice postOffice) {
            return new MetricsPubMessageReport(this.registry, this.name, this.filter, this.rateUnit, this.durationUnit, this.executor, this.shutdownExecutorOnStop, this.disabledMetricAttributes, postOffice);
        }
    }
}
