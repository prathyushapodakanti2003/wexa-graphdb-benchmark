package ai.wexa.benchmark.config;

import io.github.cdimascio.dotenv.Dotenv;

import java.util.ArrayList;
import java.util.List;

/** Global run parameters, read from environment variables (see .env.example). */
public final class BenchmarkConfig {

    public final String datasetPath;
    public final int batchSize;
    public final int readIterations;
    public final List<Integer> mixedConcurrencyLevels;
    public final int mixedDurationSeconds;
    public final double mixedReadRatio;

    private BenchmarkConfig(String datasetPath, int batchSize, int readIterations,
                             List<Integer> mixedConcurrencyLevels, int mixedDurationSeconds,
                             double mixedReadRatio) {
        this.datasetPath = datasetPath;
        this.batchSize = batchSize;
        this.readIterations = readIterations;
        this.mixedConcurrencyLevels = mixedConcurrencyLevels;
        this.mixedDurationSeconds = mixedDurationSeconds;
        this.mixedReadRatio = mixedReadRatio;
    }

    public static BenchmarkConfig fromEnv(Dotenv dotenv) {
        List<Integer> levels = new ArrayList<>();
        for (String s : getOr(dotenv, "BENCHMARK_MIXED_CONCURRENCY_LEVELS", "1,10,40").split(",")) {
            levels.add(Integer.parseInt(s.trim()));
        }
        return new BenchmarkConfig(
                getOr(dotenv, "DATASET_PATH", "data/musae_facebook_edges.csv"),
                Integer.parseInt(getOr(dotenv, "BENCHMARK_BATCH_SIZE", "1000")),
                Integer.parseInt(getOr(dotenv, "BENCHMARK_READ_ITERATIONS", "100")),
                levels,
                Integer.parseInt(getOr(dotenv, "BENCHMARK_MIXED_DURATION_SECONDS", "30")),
                Double.parseDouble(getOr(dotenv, "BENCHMARK_MIXED_READ_RATIO", "0.8")));
    }

    private static String getOr(Dotenv dotenv, String key, String fallback) {
        String value = dotenv.get(key);
        return (value == null || value.isBlank()) ? fallback : value;
    }
}
