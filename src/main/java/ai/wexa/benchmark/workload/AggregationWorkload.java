package ai.wexa.benchmark.workload;

import ai.wexa.benchmark.metrics.LatencyRecorder;
import ai.wexa.benchmark.platform.GraphPlatform;

/** Measures count-aggregation-over-relationship-type latency, with warm-up. */
public final class AggregationWorkload {

    private static final int WARMUP_ITERATIONS = 5;

    private AggregationWorkload() {}

    public static LatencyRecorder.PercentileResult run(GraphPlatform platform, int iterations) {
        LatencyRecorder recorder = new LatencyRecorder();

        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            platform.aggregateCountByRelationshipType();
        }
        for (int i = 0; i < iterations; i++) {
            long startNanos = System.nanoTime();
            platform.aggregateCountByRelationshipType();
            recorder.recordNanos(System.nanoTime() - startNanos);
        }

        return recorder.toResult();
    }
}
