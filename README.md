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
| CognoDB Cloud | 0.5 (burstable) | 256 MB | 1 GB | assignment spec |
| Neo4j AuraDB Free | _fill in from console_ | _fill in_ | _fill in_ | AuraDB Free instance details page |
| Memgraph Cloud | _fill in_ | _fill in_ | _fill in_ | Memgraph Cloud instance details page |
| ArangoDB Oasis | _fill in_ | _fill in_ | _fill in_ | Oasis deployment details page |
| TigerGraph Cloud | _fill in_ | _fill in_ | _fill in_ | TigerGraph Cloud instance details page |

If any platform's free tier is meaningfully larger than CognoDB's 0.5 vCPU / 256 MB, that is
itself a fairness caveat — record it here rather than silently ignoring the mismatch.

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

Run `mvn exec:java -Dexec.args=report` after benchmarking every platform to populate this section
automatically from `results/*.json`. A worked example of the generated output (from a local Docker
Neo4j smoke test, *not* real cloud numbers) is in `results/sample/`.

<!-- RESULTS:END -->

## Analysis

_To be filled in after a real run: what the numbers show, and where explainable, why the
platforms differ (e.g., Bolt-protocol platforms sharing near-identical client-side overhead vs.
AQL's document-first execution model vs. TigerGraph's native MPP engine and REST-based access
pattern)._

## Known caveats (methodology, not results)

- The TigerGraph adapter (`TigerGraphPlatform`) talks to TigerGraph Cloud's REST++/GSQL API,
  which has no official low-level Java driver and whose exact endpoint paths differ across
  TigerGraph versions and cloud tenant configurations. Unlike the other four adapters, it could
  not be smoke-tested against a live instance before an account existed — verify the endpoint
  shapes in `TigerGraphPlatform.java` against the actual provisioned instance's API docs before
  trusting its numbers, and record any adjustment made here.
- Mixed-workload writes on TigerGraph reuse the `LINK` edge type (TigerGraph's schema is fixed at
  graph-creation time, unlike the other platforms which can add a throwaway relationship/edge
  type on the fly) — see the caveat in `TigerGraphPlatform.cleanupMixedWrites()`.
- Free-tier throttling, cold-start effects and network variance between the client machine and
  each platform's region are expected; re-run `report` after multiple `run` passes if you want to
  quantify run-to-run variance, and note it here.

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
