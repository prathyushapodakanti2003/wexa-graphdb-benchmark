package ai.wexa.benchmark.metrics;

import java.util.List;
import java.util.Map;

/** Full set of section-5.2 metrics collected for one platform in one run. */
public record BenchmarkResult(
        String platform,
        String queryLanguage,
        long nodeCount,
        long relationshipCount,
        double loadWallClockSeconds,
        double nodesPerSecond,
        double relationshipsPerSecond,
        Map<String, LatencyRecorder.PercentileResult> traversalLatencyMs, // "1hop","2hop","3hop"
        LatencyRecorder.PercentileResult pointLookupLatencyMs,
        LatencyRecorder.PercentileResult filteredLookupLatencyMs,
        LatencyRecorder.PercentileResult aggregationLatencyMs,
        Map<Integer, Double> mixedThroughputQps, // concurrency -> sustained queries/sec
        String indexedProperties,
        String footprintNotes,
        List<String> caveats
) {}
