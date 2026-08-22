package ai.wexa.benchmark.config;

import ai.wexa.benchmark.platform.GraphPlatform;
import ai.wexa.benchmark.platform.impl.ArangoDbPlatform;
import ai.wexa.benchmark.platform.impl.Neo4jDriverPlatform;
import ai.wexa.benchmark.platform.impl.TigerGraphPlatform;
import io.github.cdimascio.dotenv.Dotenv;

import java.util.List;

/** Builds a {@link GraphPlatform} for one of the five known platform keys from .env values. */
public final class PlatformRegistry {

    public static final List<String> ALL_PLATFORM_KEYS =
            List.of("cognodb", "aura", "memgraph", "arangodb", "tigergraph");

    private final Dotenv dotenv;

    public PlatformRegistry(Dotenv dotenv) {
        this.dotenv = dotenv;
    }

    public GraphPlatform build(String key) {
        return switch (key) {
            case "cognodb" -> new Neo4jDriverPlatform(
                    "CognoDB Cloud", require("COGNODB_URI"), require("COGNODB_USER"), require("COGNODB_PASSWORD"));
            case "aura" -> new Neo4jDriverPlatform(
                    "Neo4j AuraDB Free", require("AURA_URI"), require("AURA_USER"), require("AURA_PASSWORD"));
            case "memgraph" -> new Neo4jDriverPlatform(
                    "Memgraph Cloud", require("MEMGRAPH_URI"), require("MEMGRAPH_USER"), require("MEMGRAPH_PASSWORD"));
            case "arangodb" -> new ArangoDbPlatform(
                    require("ARANGO_URI"), require("ARANGO_USER"), require("ARANGO_PASSWORD"),
                    dotenv.get("ARANGO_DATABASE", "benchmark"));
            case "tigergraph" -> new TigerGraphPlatform(
                    require("TIGERGRAPH_HOST"), require("TIGERGRAPH_USER"), require("TIGERGRAPH_PASSWORD"),
                    dotenv.get("TIGERGRAPH_GRAPH", "benchmark"));
            default -> throw new IllegalArgumentException(
                    "Unknown platform key '" + key + "', expected one of " + ALL_PLATFORM_KEYS);
        };
    }

    private String require(String key) {
        String value = dotenv.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    "Missing " + key + " - copy .env.example to .env and fill in real credentials.");
        }
        return value;
    }
}
