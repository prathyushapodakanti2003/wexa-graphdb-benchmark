### Data loading

| Platform | Nodes | Relationships | Nodes/sec | Relationships/sec | Wall-clock load time |
|---|---|---|---|---|---|
| CognoDB Cloud | 22470 | 171002 | 3638.3 | 27688.0 | 6.2s |

### Traversals (p50 / p95 ms)

| Platform | 1-hop | 2-hop | 3-hop |
|---|---|---|---|
| CognoDB Cloud | 5.86 / 11.28 (n=20) | 5.67 / 10.55 (n=20) | 3.96 / 12.05 (n=20) |

### Lookups (p50 / p95 ms)

| Platform | Point lookup | Filtered/indexed lookup | Indexed properties |
|---|---|---|---|
| CognoDB Cloud | 4.13 / 6.18 (n=20) | 4.72 / 6.00 (n=20) | Page.id (single-property index, CREATE INDEX ... FOR (p:Page) ON (p.id)) |

### Aggregation (p50 / p95 ms)

| Platform | Count over relationship type |
|---|---|
| CognoDB Cloud | 3.19 / 5.14 (n=20) |

### Mixed read/write workload (sustained queries/sec)

| Platform | 1 clients | 5 clients |
|---|---|---|
| CognoDB Cloud | 339.6 | 1507.8 |

### Footprint

| Platform | Notes |
|---|---|
| CognoDB Cloud | not observable via Cypher on the free tier: stored data size and memory usage are only shown in the platform's cloud console (record manually). Queryable counts: 22470 nodes, 171002 relationships. |

### Caveats

