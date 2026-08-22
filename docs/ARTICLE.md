# We put five free-tier graph databases through the same 171,000-edge gauntlet

*A methodology-first benchmark of CognoDB Cloud, Neo4j AuraDB, Memgraph Cloud, ArangoDB Oasis and
TigerGraph Cloud — same data, same queries, same tiny hardware budget (well, almost — more on that
below).*

## Why benchmark graph databases on hardware this small?

Free tiers are usually where people first meet a database, and they're usually where benchmark
marketing goes to die — vendors love comparing their paid tier against a competitor's free one.
So the constraint here was the opposite: pick the smallest, most burstable tier one of these five
offers (CognoDB's free instance: burst to 0.5 vCPU, 512 MB RAM, 1 GiB disk) and hold every other
platform to the same ceiling. Two of the five didn't actually fit that ceiling — more on that below
too, because pretending otherwise would defeat the point of doing this honestly.

## The setup, in one paragraph

All five platforms loaded the identical [MUSAE Facebook Large Page-Page
Network](https://snap.stanford.edu/data/facebook-large-page-page-network.html) dataset — 22,470
nodes, 171,002 edges, a real (if modest) social graph, not a synthetic toy. The same Java harness
ran the same logical queries against every platform: 1/2/3-hop traversals from randomly sampled
start nodes, point and range-filtered lookups, a count aggregation, and a concurrency-swept mixed
read/write workload. Three of the five (CognoDB, AuraDB, Memgraph) speak the exact same wire
protocol and query language — Bolt and Cypher — through the exact same driver, which turns out to
be its own interesting result: it isolates how much of any latency difference is really the
*platform* versus just being a different Cypher engine on different hardware.

## What we found

The headline isn't "database X wins." It's that **every platform's read latency came back flat
across query complexity** — 1-hop, 2-hop, 3-hop traversal, a point lookup, and a full aggregation
all landed within a few percent of each other *on the same platform*. AuraDB answered all five at
~102ms. CognoDB answered all five at ~500ms. ArangoDB answered all five at ~18ms. At this dataset
size, round-trip and per-query session overhead dominates actual query execution cost across the
board — so what's really being compared is each platform's baseline latency, not how well it
handles a harder query.

And that baseline ranking does not track hardware at all, which is the actual surprise here:

| Platform | Traversal p50 | Free-tier hardware (observed) |
|---|---|---|
| ArangoDB Oasis | ~18ms | not confirmed |
| Neo4j AuraDB Free | ~102ms | not confirmed |
| TigerGraph Cloud | ~233ms | 2 vCPU / 16 GiB |
| CognoDB Cloud | ~500ms | 0.5 vCPU / 512 MB |
| Memgraph Cloud | ~513ms | 2 vCPU / 2 GB |

TigerGraph had roughly **32x** CognoDB's RAM and was still the second-slowest platform tested.
Memgraph had **4x** CognoDB's hardware and performed identically to it. Whatever's driving these
numbers, it isn't raw compute — the likelier culprits are network round-trip distance to each
platform's region, connection/session overhead per call, and, specific to TigerGraph, the cost of
compiling an ad-hoc `INTERPRET QUERY` fresh on every request instead of using a pre-installed GSQL
query.

**Ingest throughput told a different story** — the one metric here that's genuinely
compute/IO-bound rather than round-trip-bound. AuraDB and ArangoDB both loaded the full dataset at
~760-784 nodes/sec; CognoDB and Memgraph both loaded at ~207-218 nodes/sec, a 3.5x gap; TigerGraph
sat in between at ~361. Bulk loading is a much fairer proxy for raw platform throughput than any of
the single-record read numbers above.

**Under concurrency, the two fastest platforms scaled differently.** AuraDB's mixed-workload
throughput scaled almost linearly with client count — 9 → 94 → 363 qps across 1/10/40 clients.
ArangoDB started far ahead at 1 client (51 qps) but scaled sub-linearly to 40 clients (only ~251
qps, about 5x for a 40x concurrency increase) — its edge looks like single-connection speed more
than concurrent-connection headroom, possibly a connection or thread ceiling on this trial
deployment.

## Honest caveats

- **Two platforms weren't actually on comparable hardware.** Memgraph Cloud's free project came in
  at 2 vCPU / 2 GB and TigerGraph Cloud's workspace at 2 vCPU / 16 GiB — 4x and roughly 32x
  CognoDB's actual 0.5 vCPU / 512 MB, respectively. Both are recorded here rather than hidden, and
  it's exactly why neither's underwhelming latency can be blamed on thin hardware — they had far
  more of it than CognoDB and still didn't come out ahead.
- **AuraDB Free and ArangoDB Oasis's exact instance specs weren't captured during this run.** Their
  results are real, but the fairness comparison for those two is incomplete without that number.
- **ArangoDB's 3-hop traversal p95 spiked to 735ms** against an 18ms p50 — a real tail-latency
  outlier, reported as-is rather than averaged away, most likely a one-off network blip on the
  client-to-Oasis path rather than a systematic issue.
- **TigerGraph Cloud's REST adapter needed live fixes.** It has no official Java driver, and its
  newer "Savanna" cloud platform uses a per-workspace hostname not shown anywhere in the console
  (found via the browser's Network tab) and secret-header authentication instead of the
  username/password token exchange its classic docs describe. Both were discovered and corrected
  against a real workspace during this project, not verified in advance.
- **ArangoDB Oasis's trial expires 14 days after deployment** — unlike the other four platforms'
  indefinite free tiers, so reproducing this exact run later will need a fresh trial deployment.

## Try it yourself

The full harness — five platform adapters behind one `GraphPlatform` interface, one command per
platform, a generated results matrix — is open source:
**https://github.com/prathyushapodakanti2003/wexa-graphdb-benchmark**. Bring your own free-tier
accounts and `mvn exec:java -Dexec.args=run-all` reproduces every number above.
