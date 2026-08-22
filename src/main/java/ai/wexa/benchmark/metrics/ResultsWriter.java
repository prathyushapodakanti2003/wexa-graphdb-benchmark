package ai.wexa.benchmark.metrics;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Persists raw results as JSON and renders the section-5.2 results matrix as Markdown tables. */
public final class ResultsWriter {

    private final ObjectMapper json = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    public void writeJson(Path path, BenchmarkResult result) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, json.writeValueAsString(result));
    }

    public String renderResultsMatrix(List<BenchmarkResult> results) {
        StringBuilder md = new StringBuilder();

        md.append("### Data loading\n\n");
        md.append("| Platform | Nodes | Relationships | Nodes/sec | Relationships/sec | Wall-clock load time |\n");
        md.append("|---|---|---|---|---|---|\n");
        for (BenchmarkResult r : results) {
            md.append("| %s | %d | %d | %.1f | %.1f | %.1fs |\n".formatted(
                    r.platform(), r.nodeCount(), r.relationshipCount(),
                    r.nodesPerSecond(), r.relationshipsPerSecond(), r.loadWallClockSeconds()));
        }

        md.append("\n### Traversals (p50 / p95 ms)\n\n");
        md.append("| Platform | 1-hop | 2-hop | 3-hop |\n|---|---|---|---|\n");
        for (BenchmarkResult r : results) {
            md.append("| %s | %s | %s | %s |\n".formatted(
                    r.platform(),
                    fmt(r.traversalLatencyMs().get("1hop")),
                    fmt(r.traversalLatencyMs().get("2hop")),
                    fmt(r.traversalLatencyMs().get("3hop"))));
        }

        md.append("\n### Lookups (p50 / p95 ms)\n\n");
        md.append("| Platform | Point lookup | Filtered/indexed lookup | Indexed properties |\n|---|---|---|---|\n");
        for (BenchmarkResult r : results) {
            md.append("| %s | %s | %s | %s |\n".formatted(
                    r.platform(), fmt(r.pointLookupLatencyMs()), fmt(r.filteredLookupLatencyMs()),
                    r.indexedProperties()));
        }

        md.append("\n### Aggregation (p50 / p95 ms)\n\n");
        md.append("| Platform | Count over relationship type |\n|---|---|\n");
        for (BenchmarkResult r : results) {
            md.append("| %s | %s |\n".formatted(r.platform(), fmt(r.aggregationLatencyMs())));
        }

        md.append("\n### Mixed read/write workload (sustained queries/sec)\n\n");
        Map<Integer, Boolean> concurrencyLevels = new TreeMap<>();
        for (BenchmarkResult r : results) {
            for (Integer c : r.mixedThroughputQps().keySet()) concurrencyLevels.put(c, true);
        }
        md.append("| Platform |");
        for (Integer c : concurrencyLevels.keySet()) md.append(" %d clients |".formatted(c));
        md.append("\n|---|").append("---|".repeat(concurrencyLevels.size())).append("\n");
        for (BenchmarkResult r : results) {
            md.append("| %s |".formatted(r.platform()));
            for (Integer c : concurrencyLevels.keySet()) {
                Double qps = r.mixedThroughputQps().get(c);
                md.append(qps == null ? " n/a |" : " %.1f |".formatted(qps));
            }
            md.append("\n");
        }

        md.append("\n### Footprint\n\n");
        md.append("| Platform | Notes |\n|---|---|\n");
        for (BenchmarkResult r : results) {
            md.append("| %s | %s |\n".formatted(r.platform(), r.footprintNotes()));
        }

        md.append("\n### Caveats\n\n");
        for (BenchmarkResult r : results) {
            if (r.caveats().isEmpty()) continue;
            md.append("- **%s**: %s\n".formatted(r.platform(), String.join("; ", r.caveats())));
        }

        return md.toString();
    }

    private static String fmt(LatencyRecorder.PercentileResult p) {
        if (p == null) return "n/a";
        return "%.2f / %.2f (n=%d)".formatted(p.p50Ms(), p.p95Ms(), p.iterations());
    }

    /** Minimal self-contained inline SVG bar chart, no external charting dependency. */
    public String barChartSvg(String title, Map<String, Double> series, String unit) {
        int width = 640;
        int barHeight = 28;
        int gap = 10;
        int leftMargin = 180;
        int height = series.size() * (barHeight + gap) + 40;
        double max = series.values().stream().mapToDouble(Double::doubleValue).max().orElse(1.0);

        StringBuilder svg = new StringBuilder();
        svg.append("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"%d\" height=\"%d\" font-family=\"sans-serif\" font-size=\"12\">\n"
                .formatted(width, height));
        svg.append("<text x=\"10\" y=\"18\" font-size=\"14\" font-weight=\"bold\">%s</text>\n".formatted(escape(title)));

        int y = 30;
        for (Map.Entry<String, Double> e : series.entrySet()) {
            double value = e.getValue();
            int barWidth = (int) Math.round((width - leftMargin - 60) * (value / max));
            svg.append("<text x=\"5\" y=\"%d\" text-anchor=\"start\">%s</text>\n"
                    .formatted(y + barHeight - 9, escape(e.getKey())));
            svg.append("<rect x=\"%d\" y=\"%d\" width=\"%d\" height=\"%d\" fill=\"#4C6EF5\" />\n"
                    .formatted(leftMargin, y, Math.max(1, barWidth), barHeight - 6));
            svg.append("<text x=\"%d\" y=\"%d\">%.2f %s</text>\n"
                    .formatted(leftMargin + barWidth + 6, y + barHeight - 9, value, unit));
            y += barHeight + gap;
        }
        svg.append("</svg>\n");
        return svg.toString();
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
