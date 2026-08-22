# Graph Database Cloud Benchmarking: CognoDB vs. four managed graph platforms

A reproducible, fair benchmark of [CognoDB Cloud](https://console.cognodb.com) against four other
managed graph database platforms, built for the Wexa AI Backend Engineer take-home assignment.

This is not about which database "wins" — it's about measuring all five under identical data,
queries and resource limits, and reporting the results (and their caveats) honestly.

## Platforms compared

| Platform | Query language | Client | Why it's here |
|---|---|---|---|
| [CognoDB Cloud](https://console.cognodb.com/signup) | Cypher over Bolt | official `neo4j-java-driver` | the platform under evaluation |
| [Neo4j AuraDB Free](https://neo4j.com/cloud/aura-free/) | Cypher over Bolt | same driver, different URI | same protocol/driver as CognoDB — isolates *platform* differences from *client* differences |
| [Memgraph Cloud](https://memgraph.com/cloud) | Cypher over Bolt | same driver, different URI | third Bolt+Cypher platform, different storage engine (in-memory-first) |
| [ArangoDB Oasis](https://cloud.arangodb.com/) | AQL | official `arangodb-java-driver` | multi-model document+graph store, architecturally distinct query model |
| [TigerGraph Cloud](https://tgcloud.io/) | GSQL via REST++ | Java `HttpClient` | native MPP graph engine, most architecturally distinct of the five |

Three platforms share one adapter (`Neo4jDriverPlatform`) because they all speak Bolt + Cypher via
the same official driver — same code path, same queries, for a fair three-way comparison. ArangoDB
and TigerGraph get dedicated adapters since their query languages differ.

## Fairness: same resources everywhere

Every platform must run on the free/entry tier, with specs recorded here **exactly as shown in
each platform's console** — do not guess:

| Platform | vCPU | RAM | Storage | Source |
|---|---|---|---|---|
| CognoDB Cloud | burst to 0.5 vCPU | 512 MB | 1 GiB | CognoDB console, "c0" free instance size (N. Virginia / us-east4) — note: the assignment PDF states 256 MB, but the live console shows 512 MB; recorded as observed, not as written |
| Neo4j AuraDB Free | not confirmed | not confirmed | not confirmed | not captured from the console during this run — AuraDB Free's instance details page has the exact figures; fill in before treating its latency numbers as fully explained |
| Memgraph Cloud | 2 | 2 GB | not shown | Memgraph Cloud project overview (Europe/Frankfurt) — ~4x CognoDB's vCPU and RAM |
| ArangoDB Oasis | not confirmed | not confirmed | not confirmed | Oasis deployment overview showed endpoint/region (AWS, Asia Pacific/Mumbai) but not instance size during this run — check the deployment's configuration page |
| TigerGraph Cloud | 2 | 16 GiB | not shown | TigerGraph Cloud (Savanna) Workspace tab (us-east-1) — ~4x vCPU and ~32x RAM vs. CognoDB, the largest gap of any platform here |

Two of the five platforms (Memgraph, TigerGraph) turned out to run on hardware far larger than
CognoDB's actual free tier — not resource-equivalent at all. Per the assignment's own guidance,
that's recorded here honestly rather than hidden or worked around: any speed advantage those two
show in the results below is confounded by hardware, not just platform/query-language differences.
AuraDB and ArangoDB's exact specs were not captured during this run and are marked accordingly
rather than assumed.

## Dataset

[MUSAE Facebook Large Page-Page Network](https://snap.stanford.edu/data/facebook-large-page-page-network.html)
(SNAP, Rozemberczki et al. 2019): **22,470 nodes, 171,002 edges** — public, single CSV edge list,
comfortably inside the assignment's 100k–500k relationship target with no sampling required.
SNAP's own download link for this dataset is dead ("Not available" on the page as of this
writing), so `download` (below) mirrors it from the
[original paper authors' repo](https://github.com/benedekrozemberczki/MUSAE) — verified to match
SNAP's stated edge count exactly (171,002 lines after the header).

## Prerequisites

- JDK 21+ and Maven 3.9+ (`java -version`, `mvn -version`)
- A free-tier/trial account on all five platforms above, with connection details saved
- Docker, only if you want to smoke-test the Bolt/Cypher adapter locally before wiring real cloud
  credentials (optional, not required to run the real benchmark)

## Setup

1. Create the five accounts (links in the table above) and note each platform's connection URI
   and credentials as you go — most show the password/token exactly once.
2. `cp .env.example .env` and fill in every value. `.env` is git-ignored; credentials are never
   read from anywhere else, per the assignment's rule against committing secrets.
3. For **TigerGraph Cloud** specifically: create a graph in the console named to match
   `TIGERGRAPH_GRAPH`, and define a `Page` vertex type (integer primary id) and a directed `LINK`
   edge type `Page -> Page` via GSQL before running the benchmark — this benchmark measures query
   and load performance, not schema design, so schema creation is a one-time manual step.
4. `mvn compile` to build.

## Running the benchmark

```
mvn exec:java -Dexec.args=download                # fetch the dataset into data/
mvn exec:java -Dexec.args="run cognodb"            # load + benchmark one platform
mvn exec:java -Dexec.args="run aura"
mvn exec:java -Dexec.args="run memgraph"
mvn exec:java -Dexec.args="run arangodb"
mvn exec:java -Dexec.args="run tigergraph"
mvn exec:java -Dexec.args=report                   # renders results/RESULTS.md and updates
                                                     # this README's results section below
```

Or build once and run the shaded jar directly: `mvn package` then
`java -jar target/benchmark.jar <download|run <platform>|run-all|report>`.

Each `run` loads the dataset fresh (wiping any prior run's data on that platform), then executes,
in order: the ingest measurement (part of load), traversal (1/2/3-hop), point + filtered lookup,
count aggregation, and the mixed read/write concurrency sweep — writing
`results/<platform>.json`. `report` aggregates whatever `results/*.json` files exist into a single
matrix; it's safe to run after each platform or once at the end.

## Methodology

- **Same dataset, same logical queries, same client machine and region** for every platform —
  enforced by construction: every workload class takes a `GraphPlatform`, never a
  platform-specific type, so the exact same Java code drives all five.
- **Warm-up before measuring**: 10 warm-up calls before the traversal/lookup workloads, 5 before
  aggregation, discarded from the recorded percentiles.
- **Percentiles, not just averages**: `LatencyRecorder` uses HdrHistogram over
  `BENCHMARK_READ_ITERATIONS` (default 100) iterations per read workload, reporting p50/p95.
- **Mixed workload**: a concurrency sweep (default 1/10/40 clients, `BENCHMARK_MIXED_CONCURRENCY_LEVELS`)
  for `BENCHMARK_MIXED_DURATION_SECONDS` (default 30s) per level, at an 80/20 read/write mix
  (`BENCHMARK_MIXED_READ_RATIO`). Writes are tagged separately (`MIXED_WRITE` relationship type,
  or an id-range convention on TigerGraph) and cleaned up after each concurrency level so repeated
  runs don't grow the dataset.
- **Indexing**: every platform gets a single index/unique-constraint on the node id property used
  for point/filtered lookups (see each platform's `describeIndexedProperties()`); this is recorded
  per-platform in the results matrix.
- **Footprint**: reported as whatever each platform's driver/API exposes; every adapter reports
  "not observable via API — see console" rather than guessing, since none of the five expose
  stored-bytes/memory over Bolt, AQL or GSQL on their free tiers.

## Results

<!-- RESULTS:START -->

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

<!-- RESULTS:END -->

## Analysis

**Every platform's read latency is flat across query complexity — not just one of them.** 1-hop,
2-hop, 3-hop traversal, point lookup, and aggregation all land within a few percent of each other
*within* each platform (e.g. AuraDB: ~102ms across all five; CognoDB: ~500ms across all five;
ArangoDB: ~17-19ms p50 across all five). At this dataset size, round-trip/session overhead per
query dominates actual query execution cost on every platform tested, not just the one flagged
earlier in this process. That means the per-platform *baseline* latency is really what's being
compared here, not query-specific execution differences.

**That baseline ranking doesn't track hardware at all** — which is the most interesting finding of
this benchmark. Ranked fastest to slowest baseline (p50, ms): ArangoDB Oasis (~18) → AuraDB Free
(~102) → TigerGraph Cloud (~233) → CognoDB Cloud (~500) ≈ Memgraph Cloud (~513). TigerGraph has
roughly *32x* CognoDB's hardware and is still 2nd-slowest; Memgraph has *4x* CognoDB's hardware and
performs identically to it. Whatever is driving these differences, it isn't raw vCPU/RAM — more
likely candidates are network round-trip distance to each platform's region, per-query connection/
session overhead, and (for TigerGraph specifically) the cost of compiling an `INTERPRET QUERY`
fresh on every call rather than using a pre-installed, pre-compiled GSQL query.

**Ingest throughput is the one metric that does track something structural**: AuraDB and ArangoDB
both load at ~760-784 nodes/sec, CognoDB and Memgraph both load at ~207-218 nodes/sec (roughly a
3.5x gap), with TigerGraph in between at ~361. Bulk loading is genuinely throughput-bound in a way
single-record lookups aren't, so this is closer to a fair comparison of each platform's write path
than the flat-latency read numbers above are.

**Mixed-workload scaling behavior differs in a way worth a second look**: AuraDB scales its
throughput almost linearly with concurrency (9 → 94 → 363 qps across 1/10/40 clients, roughly
proportional to the concurrency increase), while ArangoDB starts far ahead at low concurrency
(51 qps at 1 client) but scales sub-linearly (only ~5x to 251 qps at 40x the clients) — suggesting
ArangoDB's advantage is mostly single-connection speed rather than concurrent-connection headroom,
possibly bumping into a connection or thread limit on this trial deployment.

**One honest outlier**: ArangoDB's 3-hop traversal p95 spiked to 735ms against a 19ms p50 — a large
tail-latency blip not present at 1-hop or 2-hop. Reported as-is rather than smoothed over; likely a
one-off network or scheduling hiccup on the client-to-Oasis path rather than a systematic issue,
but it's exactly the kind of variance the assignment asks to be surfaced honestly, not cherry-picked
away.

**Bottom line**: this benchmark's biggest lesson isn't "which database is fastest" — it's that on
tiny free-tier hardware with a modest dataset, round-trip and connection overhead swamp query
execution cost, and two of the five platforms (Memgraph, TigerGraph) had meaningfully larger free
tiers than CognoDB's, which alone would predict an advantage neither actually delivered. That
mismatch between expected and observed performance is arguably the most useful finding here.

## Known caveats (methodology, not results)

Per-platform fairness and reliability caveats discovered while producing the results above (the
Memgraph/TigerGraph hardware mismatch, TigerGraph's endpoint/auth scheme needing a live fix, the
ArangoDB Oasis trial's 14-day expiry) are recorded programmatically in `Main.knownCaveats()` and
appear automatically in the Results section's Caveats table above on every `report` run, rather
than living only in this static section.

Caveats that remain true regardless of any specific run:

- Mixed-workload writes on TigerGraph reuse the `LINK` edge type (TigerGraph's schema is fixed at
  graph-creation time, unlike the other platforms which can add a throwaway relationship/edge
  type on the fly) — see the note in `TigerGraphPlatform.cleanupMixedWrites()`.
- Free-tier throttling, cold-start effects and network variance between the client machine and
  each platform's region are expected; re-run `report` after multiple `run` passes if you want to
  quantify run-to-run variance.
- TigerGraph Cloud's newer "Savanna" platform exposes its REST API through a per-workspace
  hostname (`tg-<workspace-id>.tg-<tenant-id>.i.tgcloud.io`) that isn't shown anywhere in the
  console UI — it was found by inspecting the browser's Network tab while Query Editor was active.
  If this changes again in a future TigerGraph release, that's how to re-discover it.

## Repository layout

```
pom.xml                    Maven build, pinned dependency versions
.env.example                template for local credentials (copy to .env, never commit .env)
src/main/java/ai/wexa/benchmark/
  Main.java                  CLI entrypoint (download / run / run-all / report)
  config/                    env-var config + platform construction
  platform/                  GraphPlatform interface + one adapter per platform
  dataset/                   dataset download + CSV parsing
  workload/                  traversal / lookup / aggregation / mixed workload runners
  metrics/                   HdrHistogram-based latency recording, JSON + Markdown output
scripts/                    standalone dataset download scripts (bash + PowerShell)
data/                       downloaded dataset (git-ignored)
results/                    per-platform JSON + generated RESULTS.md (git-ignored except this file)
docs/ARTICLE.md              narrative write-up of the methodology and findings
```

## Dataset citation

Rozemberczki, B., Allen, C., & Sarkar, R. (2019). *Multi-Scale Attributed Node Embedding*.
Facebook Large Page-Page Network, via [SNAP](https://snap.stanford.edu/data/facebook-large-page-page-network.html).
