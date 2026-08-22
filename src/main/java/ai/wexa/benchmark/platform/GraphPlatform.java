package ai.wexa.benchmark.platform;

import ai.wexa.benchmark.dataset.Dataset;

/**
 * One implementation per graph database platform. The orchestrator (Main /
 * workload classes) never depends on a concrete platform - every metric is
 * collected through this interface so the same benchmark logic runs
 * unmodified against every platform under test.
 */
public interface GraphPlatform extends AutoCloseable {

    String name();

    String queryLanguage();

    void connect();

    /** Creates whatever index the platform needs for the point/filtered lookup workload. Idempotent. */
    void ensureIndexes();

    /** Wipes any prior run's data and bulk-loads the dataset. Returns counts and wall-clock load time. */
    LoadResult loadDataset(Dataset dataset, int batchSize);

    /** Returns the number of distinct nodes reachable at exactly {@code hops} hops from the start node. */
    long traverse(long startNodeId, int hops);

    boolean pointLookup(long nodeId);

    /** Range-filtered lookup using the index from {@link #ensureIndexes()}: counts nodes with id in [id, id+50). */
    long filteredLookup(long nodeId);

    /** Count aggregation over the relationship/edge type. */
    long aggregateCountByRelationshipType();

    /** Writes one edge, tagged separately from the loaded dataset so it can be cleaned up afterwards. */
    void writeEdge(long fromId, long toId);

    /** Removes every edge written by {@link #writeEdge}, leaving the loaded dataset untouched. */
    void cleanupMixedWrites();

    /** Whatever the platform's driver/API exposes about stored size / memory; "not observable: ..." otherwise. */
    String describeFootprint();

    String describeIndexedProperties();

    @Override
    void close();

    record LoadResult(long nodeCount, long relationshipCount, double wallClockSeconds) {}
}
