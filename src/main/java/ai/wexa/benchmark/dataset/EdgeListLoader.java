package ai.wexa.benchmark.dataset;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Parses the MUSAE Facebook Large Page-Page Network edge list (header "id_1,id_2",
 * one edge per line) into a {@link Dataset}. See scripts/download_dataset for the
 * source URL.
 */
public final class EdgeListLoader {

    private EdgeListLoader() {}

    public static Dataset load(Path csvPath) throws IOException {
        List<String> lines = Files.readAllLines(csvPath);
        List<Dataset.Edge> edges = new ArrayList<>(lines.size());
        Set<Long> nodeIds = new LinkedHashSet<>();

        boolean first = true;
        for (String line : lines) {
            if (first) {
                first = false;
                continue; // header row
            }
            if (line.isBlank()) continue;
            String[] parts = line.split(",");
            long from = Long.parseLong(parts[0].trim());
            long to = Long.parseLong(parts[1].trim());
            edges.add(new Dataset.Edge(from, to));
            nodeIds.add(from);
            nodeIds.add(to);
        }

        return new Dataset(edges, nodeIds);
    }
}
