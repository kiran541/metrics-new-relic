package com.palominolabs.metrics.newrelic;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Gauge;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricFilter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.ScheduledReporter;
import com.codahale.metrics.Snapshot;
import com.codahale.metrics.Timer;
import com.newrelic.api.agent.NewRelic;

import javax.annotation.concurrent.ThreadSafe;
import java.util.Map;
import java.util.SortedMap;
import java.util.concurrent.TimeUnit;

/**
 * A reporter for Metrics that writes to New Relic as "custom metrics".
 *
 * New Relic wants to keep custom metrics to a total of about 2000, but 2000 custom metrics can easily be reached when
 * every {@link com.codahale.metrics.Timer} can produce 15 New Relic metrics. See https://docs.newrelic.com/docs/features/custom-metric-collection
 * for more.
 *
 * To keep the number of custom metrics under control, provide appropriate implementations of {@link MetricFilter} and
 * {@link MetricAttributeFilter}.
 */
@ThreadSafe
public final class NewRelicReporter extends ScheduledReporter {

    private final MetricAttributeFilter attributeFilter;

    private final String metricNamePrefix;

    /**
     * @param registry         metric registry to get metrics from
     * @param name             reporter name
     * @param filter           metric filter
     * @param attributeFilter  metric attribute filter
     * @param rateUnit         unit for reporting rates
     * @param durationUnit     unit for reporting durations
     * @param metricNamePrefix prefix before the metric name used when naming New Relic metrics. Use "" if no prefix is
     *                         needed.
     * @see ScheduledReporter#ScheduledReporter(MetricRegistry, String, MetricFilter, TimeUnit, TimeUnit)
     */
    public NewRelicReporter(MetricRegistry registry, String name, MetricFilter filter,
        MetricAttributeFilter attributeFilter, TimeUnit rateUnit, TimeUnit durationUnit, String metricNamePrefix) {
        super(registry, name, filter, rateUnit, durationUnit);
        this.attributeFilter = attributeFilter;
        this.metricNamePrefix = metricNamePrefix;
    }

    @Override
    public void report(SortedMap<String, Gauge> gauges, SortedMap<String, Counter> counters,
        SortedMap<String, Histogram> histograms, SortedMap<String, Meter> meters, SortedMap<String, Timer> timers) {
        for (Map.Entry<String, Gauge> gaugeEntry : gauges.entrySet()) {
            doGauge(gaugeEntry.getKey(), gaugeEntry.getValue());
        }

        for (Map.Entry<String, Counter> counterEntry : counters.entrySet()) {
            String name = counterEntry.getKey();
            Counter counter = counterEntry.getValue();
            if (attributeFilter.recordCounterCount(name, counter)) {
                record(name + "/count", counter.getCount());
            }
        }

        for (Map.Entry<String, Histogram> histogramEntry : histograms.entrySet()) {
            String name = histogramEntry.getKey();
            Snapshot snapshot = histogramEntry.getValue().getSnapshot();

            Histogram metric = histogramEntry.getValue();
            doHistogramSnapshot(name, snapshot, metric);
        }

        for (Map.Entry<String, Meter> meterEntry : meters.entrySet()) {
            String name = meterEntry.getKey();
            Meter meter = meterEntry.getValue();
            doMetered(name, meter);
        }

        for (Map.Entry<String, Timer> timerEntry : timers.entrySet()) {
            Timer timer = timerEntry.getValue();
            String name = timerEntry.getKey();
            Snapshot snapshot = timer.getSnapshot();

            doTimerMetered(timer, name);
            doTimerSnapshot(timer, name, snapshot);
        }
    }

    private void doMetered(String name, Meter meter) {
        if (attributeFilter.recordMeterCount(name, meter)) {
            record(name + "/count", meter.getCount());
        }
        if (attributeFilter.recordMeterMeanRate(name, meter)) {
            record(name + "/meanRate/" + getRateUnit(), (float) convertRate(meter.getMeanRate()));
        }
        if (attributeFilter.recordMeter1MinuteRate(name, meter)) {
            record(name + "/1MinuteRate/" + getRateUnit(), (float) convertRate(meter.getOneMinuteRate()));
        }
        if (attributeFilter.recordMeter5MinuteRate(name, meter)) {
            record(name + "/5MinuteRate/" + getRateUnit(), (float) convertRate(meter.getFiveMinuteRate()));
        }
        if (attributeFilter.recordMeter15MinuteRate(name, meter)) {
            record(name + "/15MinuteRate/" + getRateUnit(), (float) convertRate(meter.getFifteenMinuteRate()));
        }
    }

    private void doTimerMetered(Timer timer, String name) {
        if (attributeFilter.recordTimerCount(name, timer)) {
            record(name + "/count", timer.getCount());
        }
        if (attributeFilter.recordTimerMeanRate(name, timer)) {
            record(name + "/meanRate/" + getRateUnit(), (float) convertRate(timer.getMeanRate()));
        }
        if (attributeFilter.recordTimer1MinuteRate(name, timer)) {
            record(name + "/1MinuteRate/" + getRateUnit(), (float) convertRate(timer.getOneMinuteRate()));
        }
        if (attributeFilter.recordTimer5MinuteRate(name, timer)) {
            record(name + "/5MinuteRate/" + getRateUnit(), (float) convertRate(timer.getFiveMinuteRate()));
        }
        if (attributeFilter.recordTimer15MinuteRate(name, timer)) {
            record(name + "/15MinuteRate/" + getRateUnit(), (float) convertRate(timer.getFifteenMinuteRate()));
        }
    }

    private void doHistogramSnapshot(String name, Snapshot snapshot, Histogram metric) {
        if (attributeFilter.recordHistogramMin(name, metric)) {
            record(name + "/min" , (float) convertDuration(snapshot.getMin()));
        }
        if (attributeFilter.recordHistogramMax(name, metric)) {
            record(name + "/max" , (float) convertDuration(snapshot.getMax()));
        }
        if (attributeFilter.recordHistogramMean(name, metric)) {
            record(name + "/mean" , (float) convertDuration(snapshot.getMean()));
        }
        if (attributeFilter.recordHistogramStdDev(name, metric)) {
            record(name + "/stdDev" , (float) convertDuration(snapshot.getStdDev()));
        }
        if (attributeFilter.recordHistogramMedian(name, metric)) {
            record(name + "/median" , (float) convertDuration(snapshot.getMedian()));
        }
        if (attributeFilter.recordHistogram75thPercentile(name, metric)) {
            record(name + "/75th" , (float) convertDuration(snapshot.get75thPercentile()));
        }
        if (attributeFilter.recordHistogram95thPercentile(name, metric)) {
            record(name + "/95th" , (float) convertDuration(snapshot.get95thPercentile()));
        }
        if (attributeFilter.recordHistogram98thPercentile(name, metric)) {
            record(name + "/98th" , (float) convertDuration(snapshot.get98thPercentile()));
        }
        if (attributeFilter.recordHistogram99thPercentile(name, metric)) {
            record(name + "/99th" , (float) convertDuration(snapshot.get99thPercentile()));
        }
        if (attributeFilter.recordHistogram999thPercentile(name, metric)) {
            record(name + "/99.9th" , (float) convertDuration(snapshot.get999thPercentile()));
        }
    }

    private void doTimerSnapshot(Timer timer, String name, Snapshot snapshot) {
        String nameSuffix = "/" + getDurationUnit();

        if (attributeFilter.recordTimerMin(name, timer)) {
            record(name + "/min" + nameSuffix, (float) convertDuration(snapshot.getMin()));
        }
        if (attributeFilter.recordTimerMax(name, timer)) {
            record(name + "/max" + nameSuffix, (float) convertDuration(snapshot.getMax()));
        }
        if (attributeFilter.recordTimerMean(name, timer)) {
            record(name + "/mean" + nameSuffix, (float) convertDuration(snapshot.getMean()));
        }
        if (attributeFilter.recordTimerStdDev(name, timer)) {
            record(name + "/stdDev" + nameSuffix, (float) convertDuration(snapshot.getStdDev()));
        }
        if (attributeFilter.recordTimerMedian(name, timer)) {
            record(name + "/median" + nameSuffix, (float) convertDuration(snapshot.getMedian()));
        }
        if (attributeFilter.recordTimer75thPercentile(name, timer)) {
            record(name + "/75th" + nameSuffix, (float) convertDuration(snapshot.get75thPercentile()));
        }
        if (attributeFilter.recordTimer95thPercentile(name, timer)) {
            record(name + "/95th" + nameSuffix, (float) convertDuration(snapshot.get95thPercentile()));
        }
        if (attributeFilter.recordTimer98thPercentile(name, timer)) {
            record(name + "/98th" + nameSuffix, (float) convertDuration(snapshot.get98thPercentile()));
        }
        if (attributeFilter.recordTimer99thPercentile(name, timer)) {
            record(name + "/99th" + nameSuffix, (float) convertDuration(snapshot.get99thPercentile()));
        }
        if (attributeFilter.recordTimer999thPercentile(name, timer)) {
            record(name + "/99.9th" + nameSuffix, (float) convertDuration(snapshot.get999thPercentile()));
        }
    }

    private void doGauge(String name, Gauge gauge) {
        Object gaugeValue = gauge.getValue();

        if (gaugeValue instanceof Number) {
            float n = ((Number) gaugeValue).floatValue();
            if (!Float.isNaN(n) && !Float.isInfinite(n) && attributeFilter.recordGaugeValue(name, gauge)) {
                record(name, n);
            }
        }
    }

    private void record(String name, float value) {
        NewRelic.recordMetric("Custom/" + metricNamePrefix + name, value);
    }
}
