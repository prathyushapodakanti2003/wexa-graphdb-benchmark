package ai.wexa.benchmark.platform.impl;

import ai.wexa.benchmark.dataset.Dataset;
import ai.wexa.benchmark.platform.GraphPlatform;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Shared adapter for every platform that speaks Bolt + Cypher via the official
 * neo4j-java-driver: CognoDB Cloud, Neo4j AuraDB Free and Memgraph Cloud all
 * construct this class with their own URI/credentials - same code path, same
 * queries, for a fair three-way comparison in addition to the ArangoDB/
 * TigerGraph adapters.
 */
public final class Neo4jDriverPlatform implements GraphPlatform {

    private final String platformName;
    private final String uri;
    private final String user;
    private final String password;
    private Driver driver;

    public Neo4jDriverPlatform(String platformName, String uri, String user, String password) {
        this.platformName = platformName;
        this.uri = uri;
        this.user = user;
        this.password = password;
    }

    @Override
    public String name() {
        return platformName;
    }

    @Override
    public String queryLanguage() {
        return "Cypher";
    }

    @Override
    public void connect() {
        driver = GraphDatabase.driver(uri, AuthTokens.basic(user, password));
        driver.verifyConnectivity();
    }

    @Override
    public void ensureIndexes() {
        try (Session session = driver.session()) {
            session.run("CREATE INDEX page_id_index IF NOT EXISTS FOR (p:Page) ON (p.id)").consume();
        }
    }

    @Override
    public LoadResult loadDataset(Dataset dataset, int batchSize) {
        long startNanos = System.nanoTime();
        try (Session session = driver.session()) {
            session.run("MATCH (n:Page) DETACH DELETE n").consume();

            List<Long> nodeIds = new ArrayList<>(dataset.nodeIds());
            for (int i = 0; i < nodeIds.size(); i += batchSize) {
                List<Long> batch = nodeIds.subList(i, Math.min(i + batchSize, nodeIds.size()));
                session.run(
                        "UNWIND $ids AS id CREATE (:Page {id: id})",
                        Map.of("ids", batch)
                ).consume();
            }

            List<Dataset.Edge> edges = dataset.edges();
            for (int i = 0; i < edges.size(); i += batchSize) {
                List<Dataset.Edge> batch = edges.subList(i, Math.min(i + batchSize, edges.size()));
                List<Map<String, Object>> rows = new ArrayList<>(batch.size());
                for (Dataset.Edge e : batch) {
                    rows.add(Map.of("from", e.from(), "to", e.to()));
                }
                session.run(
                        "UNWIND $rows AS row " +
                                "MATCH (a:Page {id: row.from}), (b:Page {id: row.to}) " +
                                "CREATE (a)-[:LINK]->(b)",
                        Map.of("rows", rows)
                ).consume();
            }
        }
        double seconds = (System.nanoTime() - startNanos) / 1_000_000_000.0;
        return new LoadResult(dataset.nodeIds().size(), dataset.edges().size(), seconds);
    }

    @Override
    public long traverse(long startNodeId, int hops) {
        String cypher = switch (hops) {
            case 1 -> "MATCH (n:Page {id:$id})-[:LINK]->(m) RETURN count(DISTINCT m) AS c";
            case 2 -> "MATCH (n:Page {id:$id})-[:LINK]->()-[:LINK]->(m) RETURN count(DISTINCT m) AS c";
            case 3 -> "MATCH (n:Page {id:$id})-[:LINK]->()-[:LINK]->()-[:LINK]->(m) RETURN count(DISTINCT m) AS c";
            default -> throw new IllegalArgumentException("unsupported hop count: " + hops);
        };
        try (Session session = driver.session()) {
            Result result = session.run(cypher, Map.of("id", startNodeId));
            return result.single().get("c").asLong();
        }
    }

    @Override
    public boolean pointLookup(long nodeId) {
        try (Session session = driver.session()) {
            Result result = session.run(
                    "MATCH (n:Page {id:$id}) RETURN n.id AS id",
                    Map.of("id", nodeId));
            return result.hasNext();
        }
    }

    @Override
    public long filteredLookup(long nodeId) {
        try (Session session = driver.session()) {
            Result result = session.run(
                    "MATCH (n:Page) WHERE n.id >= $lo AND n.id < $hi RETURN count(n) AS c",
                    Map.of("lo", nodeId, "hi", nodeId + 50));
            return result.single().get("c").asLong();
        }
    }

    @Override
    public long aggregateCountByRelationshipType() {
        try (Session session = driver.session()) {
            Result result = session.run("MATCH ()-[r:LINK]->() RETURN count(r) AS c");
            return result.single().get("c").asLong();
        }
    }

    @Override
    public void writeEdge(long fromId, long toId) {
        try (Session session = driver.session()) {
            session.run(
                    "MATCH (a:Page {id:$from}), (b:Page {id:$to}) CREATE (a)-[:MIXED_WRITE]->(b)",
                    Map.of("from", fromId, "to", toId)
            ).consume();
        }
    }

    @Override
    public void cleanupMixedWrites() {
        try (Session session = driver.session()) {
            session.run("MATCH ()-[r:MIXED_WRITE]->() DELETE r").consume();
        }
    }

    @Override
    public String describeFootprint() {
        try (Session session = driver.session()) {
            long nodeCount = session.run("MATCH (n:Page) RETURN count(n) AS c").single().get("c").asLong();
            long relCount = session.run("MATCH ()-[r:LINK]->() RETURN count(r) AS c").single().get("c").asLong();
            return "not observable via Cypher on the free tier: stored data size and memory usage are only " +
                    "shown in the platform's cloud console (record manually). Queryable counts: " +
                    nodeCount + " nodes, " + relCount + " relationships.";
        }
    }

    @Override
    public String describeIndexedProperties() {
        return "Page.id (single-property index, CREATE INDEX ... FOR (p:Page) ON (p.id))";
    }

    @Override
    public void close() {
        if (driver != null) {
            driver.close();
        }
    }
}
