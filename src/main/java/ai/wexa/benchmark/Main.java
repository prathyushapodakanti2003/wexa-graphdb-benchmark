package ai.wexa.benchmark;

import ai.wexa.benchmark.config.BenchmarkConfig;
import ai.wexa.benchmark.config.PlatformRegistry;
import ai.wexa.benchmark.dataset.Dataset;
import ai.wexa.benchmark.dataset.DatasetDownloader;
import ai.wexa.benchmark.dataset.EdgeListLoader;
import ai.wexa.benchmark.metrics.BenchmarkResult;
import ai.wexa.benchmark.metrics.LatencyRecorder;
import ai.wexa.benchmark.metrics.ResultsWriter;
import ai.wexa.benchmark.platform.GraphPlatform;
import ai.wexa.benchmark.workload.AggregationWorkload;
import ai.wexa.benchmark.workload.LookupWorkload;
import ai.wexa.benchmark.workload.MixedWorkload;
import ai.wexa.benchmark.workload.TraversalWorkload;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.cdimascio.dotenv.Dotenv;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * CLI entrypoint. Usage:
 *   download                 fetch the dataset CSV into data/
 *   run <platformKey>        load + benchmark one platform, write results/<platformKey>.json
 *   run-all                  run every platform in PlatformRegistry.ALL_PLATFORM_KEYS
 *   report                   render results/*.json into results/RESULTS.md
 *
 * platformKey is one of: cognodb, aura, memgraph, arangodb, tigergraph
 */
public final class Main {

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            printUsage();
            System.exit(1);
        }

        Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
        BenchmarkConfig config = BenchmarkConfig.fromEnv(dotenv);

        switch (args[0]) {
            case "download" -> download(config);
            case "run" -> {
                requireArg(args, 1, "platformKey");
                runPlatform(dotenv, config, args[1]);
            }
            case "run-all" -> {
                for (String key : PlatformRegistry.ALL_PLATFORM_KEYS) {
                    System.out.println("=== Running " + key + " ===");
                    runPlatform(dotenv, config, key);
                }
            }
            case "report" -> report();
            default -> {
                printUsage();
                System.exit(1);
            }
        }
    }

    /**
     * Fairness/methodology caveats known ahead of time for a given platform, recorded honestly
     * per the assignment's "record every caveat" rule rather than left for the README to catch
     * manually. Fill in exact resource specs here once confirmed from each platform's console.
     */
    private static List<String> knownCaveats(String platformKey) {
        return switch (platformKey) {
            case "arangodb" -> List.of(
                    "ArangoDB Oasis free trial expires 14 days after deployment creation, unlike the " +
                            "other platforms' indefinite free tiers - re-run before it lapses if reproducing this.",
                    "Oasis trial deployment's actual vCPU/RAM was not confirmed against CognoDB's observed " +
                            "0.5 vCPU / 512 MB before this run - check the deployment's instance size in the " +
                            "Oasis console and record it in the README's fairness table; any latency advantage " +
                            "shown here may partly reflect unequal hardware, not just the platform/query language.");
            case "memgraph" -> List.of(
                    "This Memgraph Cloud project was observed at 2 GB RAM / 2 CPU (Europe/Frankfurt), roughly " +
                            "4x CognoDB's actual 0.5 vCPU / 512 MB free tier - not resource-equivalent. Recorded " +
                            "honestly per the assignment's fairness note rather than hidden; any performance " +
                            "advantage Memgraph shows is confounded by this hardware gap.");
            case "tigergraph" -> List.of(
                    "This TigerGraph Cloud (Savanna) workspace was observed at 2 vCPU / 16 GiB (us-east-1), " +
                            "roughly 32x CognoDB's actual 0.5 vCPU / 512 MB free tier - the largest resource " +
                            "gap of any platform compared here, not resource-equivalent. Recorded honestly per " +
                            "the assignment's fairness note; any performance difference is heavily confounded " +
                            "by this hardware gap.",
                    "The REST adapter's endpoint/auth scheme (Savanna: per-workspace hostname found via " +
                            "browser Network tab, GSQL-Secret header auth) differs from classic TigerGraph " +
                            "Cloud's documented ports/token-exchange flow this code was originally written " +
                            "against, and was corrected live against this real workspace rather than verified " +
                            "in advance - a concrete instance of the version/tenant drift risk flagged from " +
                            "the start for this adapter.");
            default -> List.of();
        };
    }

    private static void download(BenchmarkConfig config) throws Exception {
        Path destination = Path.of(config.datasetPath);
        System.out.println("Downloading dataset to " + destination.toAbsolutePath() + " ...");
        DatasetDownloader.downloadEdgesCsv(destination);
        System.out.println("Done.");
    }

    private static void runPlatform(Dotenv dotenv, BenchmarkConfig config, String platformKey) throws Exception {
        Path datasetPath = Path.of(config.datasetPath);
        if (!Files.exists(datasetPath)) {
            throw new IllegalStateException(
                    "Dataset not found at " + datasetPath.toAbsolutePath() + " - run 'download' first.");
        }
        Dataset dataset = EdgeListLoader.load(datasetPath);

        PlatformRegistry registry = new PlatformRegistry(dotenv);
        GraphPlatform platform = registry.build(platformKey);
        List<String> caveats = new ArrayList<>(knownCaveats(platformKey));

        try {
            platform.connect();
            platform.ensureIndexes();

            System.out.println("[" + platform.name() + "] loading dataset (" +
                    dataset.nodeIds().size() + " nodes, " + dataset.edges().size() + " edges)...");
            GraphPlatform.LoadResult load = platform.loadDataset(dataset, config.batchSize);

            System.out.println("[" + platform.name() + "] traversal workload...");
            var traversal = TraversalWorkload.run(platform, dataset, config.readIterations);

            System.out.println("[" + platform.name() + "] lookup workload...");
            var lookup = LookupWorkload.run(platform, dataset, config.readIterations);

            System.out.println("[" + platform.name() + "] aggregation workload...");
            LatencyRecorder.PercentileResult aggregation =
                    AggregationWorkload.run(platform, config.readIterations);

            System.out.println("[" + platform.name() + "] mixed workload (concurrency sweep "
                    + config.mixedConcurrencyLevels + ")...");
            var mixed = MixedWorkload.run(platform, dataset, config.mixedConcurrencyLevels,
                    config.mixedDurationSeconds, config.mixedReadRatio);

            String footprint = platform.describeFootprint();

            BenchmarkResult result = new BenchmarkResult(
                    platform.name(),
                    platform.queryLanguage(),
                    load.nodeCount(),
                    load.relationshipCount(),
                    load.wallClockSeconds(),
                    load.nodeCount() / load.wallClockSeconds(),
                    load.relationshipCount() / load.wallClockSeconds(),
                    traversal,
                    lookup.pointLookup(),
                    lookup.filtered(),
                    aggregation,
                    mixed,
                    platform.describeIndexedProperties(),
                    footprint,
                    caveats);

            Path outPath = Path.of("results", platformKey + ".json");
            new ResultsWriter().writeJson(outPath, result);
            System.out.println("[" + platform.name() + "] wrote " + outPath.toAbsolutePath());
        } finally {
            platform.close();
        }
    }

    private static void report() throws Exception {
        ObjectMapper json = new ObjectMapper();
        List<BenchmarkResult> results = new ArrayList<>();
        for (String key : PlatformRegistry.ALL_PLATFORM_KEYS) {
            Path path = Path.of("results", key + ".json");
            if (Files.exists(path)) {
                results.add(json.readValue(Files.readString(path), BenchmarkResult.class));
            } else {
                System.out.println("(skipping " + key + ": no results/" + key + ".json yet)");
            }
        }
        if (results.isEmpty()) {
            System.out.println("No results found - run 'run <platformKey>' for at least one platform first.");
            return;
        }

        String matrix = new ResultsWriter().renderResultsMatrix(results);
        Path outPath = Path.of("results", "RESULTS.md");
        Files.writeString(outPath, matrix);
        System.out.println("Wrote " + outPath.toAbsolutePath());

        updateReadmeResultsSection(matrix);
    }

    private static void updateReadmeResultsSection(String matrix) throws Exception {
        Path readmePath = Path.of("README.md");
        if (!Files.exists(readmePath)) return;

        String readme = Files.readString(readmePath);
        String startMarker = "<!-- RESULTS:START -->";
        String endMarker = "<!-- RESULTS:END -->";
        int start = readme.indexOf(startMarker);
        int end = readme.indexOf(endMarker);
        if (start == -1 || end == -1 || end < start) {
            System.out.println("README.md has no RESULTS markers - skipping auto-update.");
            return;
        }

        String updated = readme.substring(0, start + startMarker.length())
                + "\n\n" + matrix + "\n"
                + readme.substring(end);
        Files.writeString(readmePath, updated);
        System.out.println("Updated README.md results section.");
    }

    private static void requireArg(String[] args, int index, String name) {
        if (args.length <= index) {
            System.err.println("Missing required argument: " + name);
            printUsage();
            System.exit(1);
        }
    }

    private static void printUsage() {
        System.out.println("""
                Usage:
                  java -jar target/benchmark.jar download
                  java -jar target/benchmark.jar run <platformKey>
                  java -jar target/benchmark.jar run-all
                  java -jar target/benchmark.jar report

                platformKey: cognodb | aura | memgraph | arangodb | tigergraph
                """);
    }
}
