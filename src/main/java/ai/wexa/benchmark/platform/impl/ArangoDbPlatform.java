package ai.wexa.benchmark.platform.impl;

import ai.wexa.benchmark.dataset.Dataset;
import ai.wexa.benchmark.platform.GraphPlatform;
import com.arangodb.ArangoCursor;
import com.arangodb.ArangoDB;
import com.arangodb.ArangoDatabase;
import com.arangodb.entity.BaseDocument;
import com.arangodb.entity.BaseEdgeDocument;
import com.arangodb.entity.CollectionType;
import com.arangodb.model.CollectionCreateOptions;
import com.arangodb.model.PersistentIndexOptions;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Adapter for ArangoDB Oasis, queried in AQL via the official arangodb-java-driver. */
public final class ArangoDbPlatform implements GraphPlatform {

    private static final String VERTEX_COLLECTION = "pages";
    private static final String EDGE_COLLECTION = "links";
    private static final String MIXED_EDGE_COLLECTION = "mixed_writes";

    private final String endpoint;
    private final String user;
    private final String password;
    private final String databaseName;
    private ArangoDB arangoDB;
    private ArangoDatabase db;

    public ArangoDbPlatform(String endpoint, String user, String password, String databaseName) {
        this.endpoint = endpoint;
        this.user = user;
        this.password = password;
        this.databaseName = databaseName;
    }

    @Override
    public String name() {
        return "ArangoDB Oasis";
    }

    @Override
    public String queryLanguage() {
        return "AQL";
    }

    @Override
    public void connect() {
        URI uri = URI.create(endpoint);
        int port = uri.getPort() == -1 ? 8529 : uri.getPort();
        arangoDB = new ArangoDB.Builder()
                .host(uri.getHost(), port)
                .useSsl(true)
                .user(user)
                .password(password)
                .build();

        if (!arangoDB.db(databaseName).exists()) {
            arangoDB.createDatabase(databaseName);
        }
        db = arangoDB.db(databaseName);

        if (!db.collection(VERTEX_COLLECTION).exists()) {
            db.createCollection(VERTEX_COLLECTION);
        }
        if (!db.collection(EDGE_COLLECTION).exists()) {
            db.createCollection(EDGE_COLLECTION, new CollectionCreateOptions().type(CollectionType.EDGES));
        }
        if (!db.collection(MIXED_EDGE_COLLECTION).exists()) {
            db.createCollection(MIXED_EDGE_COLLECTION, new CollectionCreateOptions().type(CollectionType.EDGES));
        }
    }

    @Override
    public void ensureIndexes() {
        db.collection(VERTEX_COLLECTION)
                .ensurePersistentIndex(List.of("pageId"), new PersistentIndexOptions().unique(true));
    }

    @Override
    public LoadResult loadDataset(Dataset dataset, int batchSize) {
        long startNanos = System.nanoTime();
        db.collection(EDGE_COLLECTION).truncate();
        db.collection(VERTEX_COLLECTION).truncate();

        List<Long> nodeIds = new ArrayList<>(dataset.nodeIds());
        for (int i = 0; i < nodeIds.size(); i += batchSize) {
            List<Long> batch = nodeIds.subList(i, Math.min(i + batchSize, nodeIds.size()));
            List<BaseDocument> docs = new ArrayList<>(batch.size());
            for (Long id : batch) {
                BaseDocument doc = new BaseDocument(String.valueOf(id));
                doc.addAttribute("pageId", id);
                docs.add(doc);
            }
            db.collection(VERTEX_COLLECTION).insertDocuments(docs);
        }

        List<Dataset.Edge> edges = dataset.edges();
        for (int i = 0; i < edges.size(); i += batchSize) {
            List<Dataset.Edge> batch = edges.subList(i, Math.min(i + batchSize, edges.size()));
            List<BaseEdgeDocument> docs = new ArrayList<>(batch.size());
            for (Dataset.Edge e : batch) {
                docs.add(new BaseEdgeDocument(
                        VERTEX_COLLECTION + "/" + e.from(),
                        VERTEX_COLLECTION + "/" + e.to()));
            }
            db.collection(EDGE_COLLECTION).insertDocuments(docs);
        }

        double seconds = (System.nanoTime() - startNanos) / 1_000_000_000.0;
        return new LoadResult(dataset.nodeIds().size(), dataset.edges().size(), seconds);
    }

    @Override
    public long traverse(long startNodeId, int hops) {
        String aql = "FOR v IN " + hops + ".." + hops + " OUTBOUND @start " + EDGE_COLLECTION +
                " RETURN DISTINCT v";
        Map<String, Object> bindVars = Map.of("start", VERTEX_COLLECTION + "/" + startNodeId);
        try (ArangoCursor<BaseDocument> cursor = db.query(aql, BaseDocument.class, bindVars)) {
            long count = 0;
            while (cursor.hasNext()) {
                cursor.next();
                count++;
            }
            return count;
        }
    }

    @Override
    public boolean pointLookup(long nodeId) {
        return db.collection(VERTEX_COLLECTION).documentExists(String.valueOf(nodeId));
    }

    @Override
    public long filteredLookup(long nodeId) {
        String aql = "FOR v IN " + VERTEX_COLLECTION +
                " FILTER v.pageId >= @lo AND v.pageId < @hi RETURN v";
        Map<String, Object> bindVars = Map.of("lo", nodeId, "hi", nodeId + 50);
        try (ArangoCursor<BaseDocument> cursor = db.query(aql, BaseDocument.class, bindVars)) {
            long count = 0;
            while (cursor.hasNext()) {
                cursor.next();
                count++;
            }
            return count;
        }
    }

    @Override
    public long aggregateCountByRelationshipType() {
        try (ArangoCursor<Long> cursor = db.query(
                "RETURN LENGTH(" + EDGE_COLLECTION + ")", Long.class)) {
            return cursor.next();
        }
    }

    @Override
    public void writeEdge(long fromId, long toId) {
        BaseEdgeDocument edge = new BaseEdgeDocument(
                VERTEX_COLLECTION + "/" + fromId,
                VERTEX_COLLECTION + "/" + toId);
        db.collection(MIXED_EDGE_COLLECTION).insertDocument(edge);
    }

    @Override
    public void cleanupMixedWrites() {
        db.collection(MIXED_EDGE_COLLECTION).truncate();
    }

    @Override
    public String describeFootprint() {
        long nodeCount = db.collection(VERTEX_COLLECTION).count().getCount();
        long edgeCount = db.collection(EDGE_COLLECTION).count().getCount();
        return "not observable via AQL in a stable form on Oasis free trial: stored data size is shown in the " +
                "Oasis console (Collections > Statistics), record manually. Queryable counts: " +
                nodeCount + " vertices, " + edgeCount + " edges.";
    }

    @Override
    public String describeIndexedProperties() {
        return "pages.pageId (persistent unique index)";
    }

    @Override
    public void close() {
        if (arangoDB != null) {
            arangoDB.shutdown();
        }
    }
}
