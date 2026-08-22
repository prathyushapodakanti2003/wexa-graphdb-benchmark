### Data loading

| Platform | Nodes | Relationships | Nodes/sec | Relationships/sec | Wall-clock load time |
|---|---|---|---|---|---|
| CognoDB Cloud | 22470 | 171002 | 218.1 | 1660.0 | 103.0s |
| Neo4j AuraDB Free | 22470 | 171002 | 783.9 | 5966.0 | 28.7s |
| Memgraph Cloud | 22470 | 171002 | 207.1 | 1576.4 | 108.5s |
| ArangoDB Oasis | 22470 | 171002 | 760.4 | 5787.1 | 29.5s |
| TigerGraph Cloud | 22470 | 171002 | 360.9 | 2746.9 | 62.3s |

### Traversals (p50 / p95 ms)

| Platform | 1-hop | 2-hop | 3-hop |
|---|---|---|---|
| CognoDB Cloud | 502.53 / 570.95 (n=100) | 503.05 / 622.85 (n=100) | 499.12 / 624.95 (n=100) |
| Neo4j AuraDB Free | 102.30 / 104.53 (n=100) | 102.56 / 105.45 (n=100) | 102.37 / 108.59 (n=100) |
| Memgraph Cloud | 513.54 / 608.70 (n=100) | 514.06 / 656.41 (n=100) | 513.54 / 588.78 (n=100) |
| ArangoDB Oasis | 17.73 / 33.14 (n=100) | 18.15 / 42.93 (n=100) | 19.05 / 735.58 (n=100) |
| TigerGraph Cloud | 232.91 / 235.80 (n=100) | 232.91 / 244.71 (n=100) | 232.91 / 331.09 (n=100) |

### Lookups (p50 / p95 ms)

| Platform | Point lookup | Filtered/indexed lookup | Indexed properties |
|---|---|---|---|
| CognoDB Cloud | 498.34 / 501.48 (n=100) | 518.52 / 613.42 (n=100) | Page.id (single-property index, CREATE INDEX ... FOR (p:Page) ON (p.id)) |
| Neo4j AuraDB Free | 101.97 / 116.33 (n=100) | 102.11 / 105.58 (n=100) | Page.id (single-property index, CREATE INDEX ... FOR (p:Page) ON (p.id)) |
| Memgraph Cloud | 514.59 / 534.25 (n=100) | 515.38 / 528.74 (n=100) | Page.id (single-property index, CREATE INDEX ... FOR (p:Page) ON (p.id)) |
| ArangoDB Oasis | 16.70 / 21.58 (n=100) | 17.47 / 31.21 (n=100) | pages.pageId (persistent unique index) |
| TigerGraph Cloud | 230.82 / 235.41 (n=100) | 232.78 / 236.19 (n=100) | Page.id (vertex primary id, indexed by default) |

### Aggregation (p50 / p95 ms)

| Platform | Count over relationship type |
|---|---|
| CognoDB Cloud | 497.81 / 589.30 (n=100) |
| Neo4j AuraDB Free | 101.91 / 107.35 (n=100) |
| Memgraph Cloud | 538.44 / 618.66 (n=100) |
| ArangoDB Oasis | 16.96 / 25.31 (n=100) |
| TigerGraph Cloud | 232.91 / 402.92 (n=100) |

### Mixed read/write workload (sustained queries/sec)

| Platform | 1 clients | 10 clients | 40 clients |
|---|---|---|---|
| CognoDB Cloud | 2.0 | 18.2 | 75.0 |
| Neo4j AuraDB Free | 9.0 | 93.9 | 363.0 |
| Memgraph Cloud | 1.9 | 16.8 | 74.0 |
| ArangoDB Oasis | 51.0 | 209.2 | 250.8 |
| TigerGraph Cloud | 4.2 | 40.0 | 161.0 |

### Footprint

| Platform | Notes |
|---|---|
| CognoDB Cloud | not observable via Cypher on the free tier: stored data size and memory usage are only shown in the platform's cloud console (record manually). Queryable counts: 22470 nodes, 171002 relationships. |
| Neo4j AuraDB Free | not observable via Cypher on the free tier: stored data size and memory usage are only shown in the platform's cloud console (record manually). Queryable counts: 22470 nodes, 171002 relationships. |
| Memgraph Cloud | not observable via Cypher on the free tier: stored data size and memory usage are only shown in the platform's cloud console (record manually). Queryable counts: 22470 nodes, 171002 relationships. |
| ArangoDB Oasis | not observable via AQL in a stable form on Oasis free trial: stored data size is shown in the Oasis console (Collections > Statistics), record manually. Queryable counts: 22470 vertices, 171002 edges. |
| TigerGraph Cloud | not observable via REST++ on TigerGraph Cloud free tier in a stable cross-version form: stored data size and memory usage are shown in the TigerGraph Cloud console, record manually. |

### Caveats

- **Memgraph Cloud**: This Memgraph Cloud project was observed at 2 GB RAM / 2 CPU (Europe/Frankfurt), roughly 4x CognoDB's actual 0.5 vCPU / 512 MB free tier - not resource-equivalent. Recorded honestly per the assignment's fairness note rather than hidden; any performance advantage Memgraph shows is confounded by this hardware gap.
- **ArangoDB Oasis**: ArangoDB Oasis free trial expires 14 days after deployment creation, unlike the other platforms' indefinite free tiers - re-run before it lapses if reproducing this.; Oasis trial deployment's actual vCPU/RAM was not confirmed against CognoDB's observed 0.5 vCPU / 512 MB before this run - check the deployment's instance size in the Oasis console and record it in the README's fairness table; any latency advantage shown here may partly reflect unequal hardware, not just the platform/query language.
- **TigerGraph Cloud**: This TigerGraph Cloud (Savanna) workspace was observed at 2 vCPU / 16 GiB (us-east-1), roughly 32x CognoDB's actual 0.5 vCPU / 512 MB free tier - the largest resource gap of any platform compared here, not resource-equivalent. Recorded honestly per the assignment's fairness note; any performance difference is heavily confounded by this hardware gap.; The REST adapter's endpoint/auth scheme (Savanna: per-workspace hostname found via browser Network tab, GSQL-Secret header auth) differs from classic TigerGraph Cloud's documented ports/token-exchange flow this code was originally written against, and was corrected live against this real workspace rather than verified in advance - a concrete instance of the version/tenant drift risk flagged from the start for this adapter.
