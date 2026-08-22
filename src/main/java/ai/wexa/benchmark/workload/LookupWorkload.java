package ai.wexa.benchmark.workload;

import ai.wexa.benchmark.dataset.Dataset;
import ai.wexa.benchmark.metrics.LatencyRecorder;
import ai.wexa.benchmark.platform.GraphPlatform;

import java.util.List;
import java.util.Random;

/** Measures point-lookup and range-filtered/indexed-lookup latency, with warm-up. */
public final class LookupWorkload {

    private static final int WARMUP_ITERATIONS = 10;
    private static final long RANDOM_SEED = 43;

    private LookupWorkload() {}

    public record Result(LatencyRecorder.PercentileResult pointLookup, LatencyRecorder.PercentileResult filtered) {}

    public static Result run(GraphPlatform platform, Dataset dataset, int iterations) {
        List<Long> nodeIds = List.copyOf(dataset.nodeIds());
        Random random = new Random(RANDOM_SEED);

        LatencyRecorder pointLookupRecorder = new LatencyRecorder();
        LatencyRecorder filteredRecorder = new LatencyRecorder();

        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            long id = nodeIds.get(random.nextInt(nodeIds.size()));
            platform.pointLookup(id);
            platform.filteredLookup(id);
        }

        for (int i = 0; i < iterations; i++) {
            long id = nodeIds.get(random.nextInt(nodeIds.size()));

            long startNanos = System.nanoTime();
            platform.pointLookup(id);
            pointLookupRecorder.recordNanos(System.nanoTime() - startNanos);

            startNanos = System.nanoTime();
            platform.filteredLookup(id);
            filteredRecorder.recordNanos(System.nanoTime() - startNanos);
        }

        return new Result(pointLookupRecorder.toResult(), filteredRecorder.toResult());
    }
}
