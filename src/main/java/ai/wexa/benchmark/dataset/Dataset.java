package ai.wexa.benchmark.dataset;

import java.util.List;
import java.util.Set;

/** In-memory representation of the edge-list dataset shared by every platform adapter. */
public record Dataset(List<Edge> edges, Set<Long> nodeIds) {

    public record Edge(long from, long to) {}
}
