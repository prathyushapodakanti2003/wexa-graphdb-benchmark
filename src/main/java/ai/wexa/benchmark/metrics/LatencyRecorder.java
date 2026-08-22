package ai.wexa.benchmark.metrics;

import org.HdrHistogram.Histogram;

/** Percentile-accurate latency recording for a single workload's repeated runs. */
public final class LatencyRecorder {

    // 1ns .. 60s range, 3 significant digits - plenty for p50/p95 of ms-scale queries.
    private final Histogram histogram = new Histogram(1, 60_000_000_000L, 3);

    public void recordNanos(long nanos) {
        histogram.recordValue(Math.max(1, nanos));
    }

    public double p50Millis() {
        return histogram.getValueAtPercentile(50.0) / 1_000_000.0;
    }

    public double p95Millis() {
        return histogram.getValueAtPercentile(95.0) / 1_000_000.0;
    }

    public long count() {
        return histogram.getTotalCount();
    }

    public PercentileResult toResult() {
        return new PercentileResult(p50Millis(), p95Millis(), count());
    }

    public record PercentileResult(double p50Ms, double p95Ms, long iterations) {}
}
