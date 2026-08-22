package ai.wexa.benchmark.workload;

import ai.wexa.benchmark.dataset.Dataset;
import ai.wexa.benchmark.platform.GraphPlatform;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Concurrency sweep for sustained read/write throughput. Reads are 1-hop
 * traversals from a random node; writes add one MIXED_WRITE-tagged edge
 * between two random nodes so they can be cleaned up without touching the
 * loaded dataset (see {@link GraphPlatform#cleanupMixedWrites()}).
 */
public final class MixedWorkload {

    private MixedWorkload() {}

    public static Map<Integer, Double> run(GraphPlatform platform, Dataset dataset, List<Integer> concurrencyLevels,
                                            int durationSeconds, double readRatio) {
        Map<Integer, Double> results = new TreeMap<>();
        List<Long> nodeIds = List.copyOf(dataset.nodeIds());

        for (int concurrency : concurrencyLevels) {
            AtomicLong completedOps = new AtomicLong();
            ExecutorService pool = Executors.newFixedThreadPool(concurrency);
            long deadlineNanos = System.nanoTime() + durationSeconds * 1_000_000_000L;

            List<Future<?>> futures = new ArrayList<>(concurrency);
            long startNanos = System.nanoTime();
            for (int t = 0; t < concurrency; t++) {
                futures.add(pool.submit(() -> {
                    Random random = new Random();
                    while (System.nanoTime() < deadlineNanos) {
                        if (random.nextDouble() < readRatio) {
                            long id = nodeIds.get(random.nextInt(nodeIds.size()));
                            platform.traverse(id, 1);
                        } else {
                            long from = nodeIds.get(random.nextInt(nodeIds.size()));
                            long to = nodeIds.get(random.nextInt(nodeIds.size()));
                            platform.writeEdge(from, to);
                        }
                        completedOps.incrementAndGet();
                    }
                }));
            }

            for (Future<?> f : futures) {
                try {
                    f.get();
                } catch (Exception e) {
                    throw new RuntimeException("mixed workload thread failed", e);
                }
            }
            double actualSeconds = (System.nanoTime() - startNanos) / 1_000_000_000.0;
            pool.shutdown();

            results.put(concurrency, completedOps.get() / actualSeconds);
            platform.cleanupMixedWrites();
        }

        return results;
    }
}
