package ai.wexa.benchmark.workload;

import ai.wexa.benchmark.dataset.Dataset;
import ai.wexa.benchmark.metrics.LatencyRecorder;
import ai.wexa.benchmark.platform.GraphPlatform;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** Measures 1/2/3-hop traversal latency from a randomly chosen set of start nodes, with warm-up. */
public final class TraversalWorkload {

    private static final int WARMUP_ITERATIONS = 10;
    private static final long RANDOM_SEED = 42; // fixed so every platform samples the same start nodes

    private TraversalWorkload() {}

    public static Map<String, LatencyRecorder.PercentileResult> run(
            GraphPlatform platform, Dataset dataset, int iterations) {
        List<Long> nodeIds = List.copyOf(dataset.nodeIds());
        Random random = new Random(RANDOM_SEED);

        Map<String, LatencyRecorder.PercentileResult> results = new LinkedHashMap<>();
        for (int hops = 1; hops <= 3; hops++) {
            LatencyRecorder recorder = new LatencyRecorder();

            for (int i = 0; i < WARMUP_ITERATIONS; i++) {
                platform.traverse(nodeIds.get(random.nextInt(nodeIds.size())), hops);
            }
            for (int i = 0; i < iterations; i++) {
                long startNodeId = nodeIds.get(random.nextInt(nodeIds.size()));
                long startNanos = System.nanoTime();
                platform.traverse(startNodeId, hops);
                recorder.recordNanos(System.nanoTime() - startNanos);
            }

            results.put(hops + "hop", recorder.toResult());
        }
        return results;
    }
}
