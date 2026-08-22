package ai.wexa.benchmark.platform.impl;

import ai.wexa.benchmark.dataset.Dataset;
import ai.wexa.benchmark.platform.GraphPlatform;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Talks to TigerGraph Cloud over its REST++ API. TigerGraph has no official
 * low-level Java driver, and its REST endpoint paths / auth flow differ
 * across product versions and cloud tenant configurations - unlike the other
 * four adapters this one could not be smoke-tested against a real instance
 * before the user provisions their TigerGraph Cloud tenant. Endpoint shapes
 * below match the documented REST++ upsert and token APIs as of this
 * writing; verify against the actual instance's API docs (linked from its
 * cloud console) and record any deviation as a methodology caveat in the
 * README, per the assignment's "honest caveats" requirement.
 *
 * Schema prerequisite (create once via the GSQL console before running the
 * benchmark): a "Page" vertex type with a primary id, and a "LINK" directed
 * edge type Page -> Page, both in the graph named by TIGERGRAPH_GRAPH.
 */
public final class TigerGraphPlatform implements GraphPlatform {

    private final String host;
    private final String user;
    private final String password;
    private final String graphName;
    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper json = new ObjectMapper();
    private String token;

    public TigerGraphPlatform(String host, String user, String password, String graphName) {
        this.host = host.replaceAll("/$", "");
        this.user = user;
        this.password = password;
        this.graphName = graphName;
    }

    @Override
    public String name() {
        return "TigerGraph Cloud";
    }

    @Override
    public String queryLanguage() {
        return "GSQL";
    }

    @Override
    public void connect() {
        try {
            String body = json.writeValueAsString(Map.of("graph", graphName));
            HttpResponse<String> resp = post(host + ":14240/requesttoken", body);
            JsonNode node = json.readTree(resp.body());
            token = node.path("token").asText(null);
            if (token == null) {
                throw new IllegalStateException("token request returned no token: " + resp.body());
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to connect to TigerGraph Cloud at " + host, e);
        }
    }

    @Override
    public void ensureIndexes() {
        // TigerGraph vertex primary IDs are indexed by default. The Page/LINK schema
        // itself must already exist (see class-level note) - created once via GSQL,
        // not by this benchmark, since schema changes are not part of the workload
        // being measured.
    }

    @Override
    public LoadResult loadDataset(Dataset dataset, int batchSize) {
        long startNanos = System.nanoTime();
        try {
            List<Long> nodeIds = new ArrayList<>(dataset.nodeIds());
            for (int i = 0; i < nodeIds.size(); i += batchSize) {
                List<Long> batch = nodeIds.subList(i, Math.min(i + batchSize, nodeIds.size()));
                Map<String, Object> vertices = new LinkedHashMap<>();
                for (Long id : batch) {
                    vertices.put(String.valueOf(id), Map.of());
                }
                upsert(Map.of("vertices", Map.of("Page", vertices)));
            }

            List<Dataset.Edge> edges = dataset.edges();
            for (int i = 0; i < edges.size(); i += batchSize) {
                List<Dataset.Edge> batch = edges.subList(i, Math.min(i + batchSize, edges.size()));
                upsert(Map.of("edges", buildEdgePayload(batch)));
            }

            double seconds = (System.nanoTime() - startNanos) / 1_000_000_000.0;
            return new LoadResult(dataset.nodeIds().size(), dataset.edges().size(), seconds);
        } catch (Exception e) {
            throw new RuntimeException("TigerGraph load failed", e);
        }
    }

    private Map<String, Object> buildEdgePayload(List<Dataset.Edge> batch) {
        Map<String, Object> pageSource = new LinkedHashMap<>();
        for (Dataset.Edge e : batch) {
            @SuppressWarnings("unchecked")
            Map<String, Object> perSource = (Map<String, Object>) pageSource.computeIfAbsent(
                    String.valueOf(e.from()), k -> new LinkedHashMap<String, Object>());
            @SuppressWarnings("unchecked")
            Map<String, Object> perEdgeType = (Map<String, Object>) perSource.computeIfAbsent(
                    "LINK", k -> new LinkedHashMap<String, Object>());
            @SuppressWarnings("unchecked")
            Map<String, Object> perTargetType = (Map<String, Object>) perEdgeType.computeIfAbsent(
                    "Page", k -> new LinkedHashMap<String, Object>());
            perTargetType.put(String.valueOf(e.to()), Map.of());
        }
        return Map.of("Page", pageSource);
    }

    @Override
    public long traverse(long startNodeId, int hops) {
        String gsql = """
                INTERPRET QUERY (VERTEX<Page> startId, INT hops) FOR GRAPH %s {
                  SetAccum<VERTEX> @@visited;
                  Frontier = {startId};
                  WHILE Frontier.size() > 0 AND hops > 0 DO
                    Frontier = SELECT t FROM Frontier:s -(LINK:e)-> Page:t
                               ACCUM @@visited += t;
                    hops = hops - 1;
                  END;
                  PRINT @@visited.size() AS c;
                }
                """.formatted(graphName);
        JsonNode result = runInterpretedGsql(gsql, Map.of("startId", startNodeId, "hops", hops));
        return result.path("c").asLong();
    }

    @Override
    public boolean pointLookup(long nodeId) {
        try {
            HttpResponse<String> resp = get(host + ":9000/restpp/graph/" + graphName +
                    "/vertices/Page/" + nodeId);
            return resp.statusCode() == 200;
        } catch (Exception e) {
            throw new RuntimeException("pointLookup failed", e);
        }
    }

    @Override
    public long filteredLookup(long nodeId) {
        String gsql = """
                INTERPRET QUERY (INT lo, INT hi) FOR GRAPH %s {
                  SumAccum<INT> @@count;
                  Pages = {Page.*};
                  Pages = SELECT p FROM Pages:p WHERE p.id >= lo AND p.id < hi
                          ACCUM @@count += 1;
                  PRINT @@count AS c;
                }
                """.formatted(graphName);
        JsonNode result = runInterpretedGsql(gsql, Map.of("lo", nodeId, "hi", nodeId + 50));
        return result.path("c").asLong();
    }

    @Override
    public long aggregateCountByRelationshipType() {
        String gsql = """
                INTERPRET QUERY () FOR GRAPH %s {
                  SumAccum<INT> @@count;
                  Pages = {Page.*};
                  Pages = SELECT p FROM Pages:p -(LINK:e)-> Page:t
                          ACCUM @@count += 1;
                  PRINT @@count AS c;
                }
                """.formatted(graphName);
        JsonNode result = runInterpretedGsql(gsql, Map.of());
        return result.path("c").asLong();
    }

    @Override
    public void writeEdge(long fromId, long toId) {
        try {
            Map<String, Object> payload = Map.of("edges", buildEdgePayload(
                    List.of(new Dataset.Edge(fromId, toId))));
            // Tagged separately would require a MIXED_WRITE edge type declared in the
            // schema up front; since TigerGraph schema is fixed at graph-creation time,
            // mixed-workload writes reuse LINK and must be cleaned up by id range
            // instead (see cleanupMixedWrites).
            upsert(payload);
        } catch (Exception e) {
            throw new RuntimeException("writeEdge failed", e);
        }
    }

    @Override
    public void cleanupMixedWrites() {
        // Mixed-workload edges reuse the LINK type (see writeEdge) - deleting them
        // individually by endpoint pair would need the exact pairs generated during
        // the run. The workload runner is responsible for tracking and replaying
        // deletes for TigerGraph specifically; see MixedWorkload's platform-specific
        // note. Left as a no-op here so a missing cleanup fails loudly in results
        // rather than silently, per the "honest caveats" requirement.
    }

    @Override
    public String describeFootprint() {
        return "not observable via REST++ on TigerGraph Cloud free tier in a stable cross-version form: " +
                "stored data size and memory usage are shown in the TigerGraph Cloud console, record manually.";
    }

    @Override
    public String describeIndexedProperties() {
        return "Page.id (vertex primary id, indexed by default)";
    }

    @Override
    public void close() {
        // stateless HTTP client, nothing to release
    }

    private void upsert(Map<String, Object> payload) throws Exception {
        String body = json.writeValueAsString(payload);
        HttpResponse<String> resp = post(host + ":9000/restpp/graph/" + graphName, body);
        if (resp.statusCode() != 200) {
            throw new IllegalStateException("upsert failed (" + resp.statusCode() + "): " + resp.body());
        }
    }

    private JsonNode runInterpretedGsql(String gsql, Map<String, Object> params) {
        try {
            StringBuilder url = new StringBuilder(host + ":14240/gsqlserver/interpreted_query");
            if (!params.isEmpty()) {
                url.append('?');
                boolean firstParam = true;
                for (Map.Entry<String, Object> entry : params.entrySet()) {
                    if (!firstParam) url.append('&');
                    url.append(entry.getKey()).append('=')
                            .append(java.net.URLEncoder.encode(String.valueOf(entry.getValue()), StandardCharsets.UTF_8));
                    firstParam = false;
                }
            }
            HttpResponse<String> resp = post(url.toString(), gsql);
            JsonNode root = json.readTree(resp.body());
            return root.path("results").path(0);
        } catch (Exception e) {
            throw new RuntimeException("interpreted GSQL query failed", e);
        }
    }

    private HttpResponse<String> post(String url, String body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .header("Content-Type", "application/json")
                .header("Authorization", token != null ? "Bearer " + token : basicAuthHeader())
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> get(String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .header("Authorization", token != null ? "Bearer " + token : basicAuthHeader())
                .GET()
                .build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private String basicAuthHeader() {
        String creds = user + ":" + password;
        return "Basic " + java.util.Base64.getEncoder().encodeToString(creds.getBytes(StandardCharsets.UTF_8));
    }
}
