# We put five free-tier graph databases through the same 171,000-edge gauntlet

*A methodology-first benchmark of CognoDB Cloud, Neo4j AuraDB, Memgraph Cloud, ArangoDB Oasis and
TigerGraph Cloud — same data, same queries, same tiny hardware budget.*

> This draft ships with placeholder numbers (`[X]`) — fill them in from your real
> `results/RESULTS.md` after running the benchmark, then this is ready to publish on dev.to,
> Medium, or wherever you want the engagement to land. That last step — actually publishing and
> getting real reads/stars/reactions — is the point of this piece existing separately from the
> README: the README is a reference; this is meant to be read start to finish by someone who
> doesn't already know what a graph database is for.

## Why benchmark graph databases on hardware this small?

Free tiers are usually where people first meet a database, and they're usually where benchmark
marketing goes to die — vendors love comparing their paid tier against a competitor's free one.
So the constraint here was the opposite: pick the smallest, most burstable tier one of these five
offers (CognoDB's free instance: 0.5 burstable vCPU, 256 MB RAM, 1 GB disk) and hold every other
platform to the same ceiling. If a platform's free tier doesn't fit that budget, it's noted, not
hidden.

## The setup, in one paragraph

All five platforms loaded the identical [MUSAE Facebook Large Page-Page
Network](https://snap.stanford.edu/data/facebook-large-page-page-network.html) dataset — 22,470
nodes, 171,002 edges, a real (if modest) social graph, not a synthetic toy. The same Java harness
ran the same logical queries against every platform: 1/2/3-hop traversals from randomly sampled
start nodes, point and range-filtered lookups, a count aggregation, and a concurrency-swept
mixed read/write workload. Three of the five (CognoDB, AuraDB, Memgraph) speak the exact same
wire protocol and query language — Bolt and Cypher — through the exact same driver, which turns
out to be its own interesting result: it isolates how much of any latency difference is really
the *platform* versus just being a different Cypher engine on different hardware.

## What we found

_[Fill in after a real run — lead with the single most interesting number, not a wall of tables.
Example shape: "CognoDB's p50 for a 2-hop traversal came in at [X] ms against AuraDB's [Y] ms on
the same query, same driver, same client — the gap is almost entirely explained by [Z]."]_

- **Ingest throughput**: [X] vs [Y] vs [Z] nodes/sec — _why this differed, if it did_
- **Traversal latency**: 1/2/3-hop p50/p95 across all five — _where the Bolt-protocol trio agreed
  and where TigerGraph's native MPP engine or ArangoDB's document-first model pulled ahead or
  behind_
- **Mixed workload under concurrency**: sustained qps at 1 / 10 / 40 clients — _which platforms
  degraded gracefully vs. fell off a cliff under the free tier's burstable CPU limit_

## Honest caveats

Free-tier benchmarks are noisy by nature — burstable CPU credits, shared-tenant network variance,
and cold-start effects all show up in the numbers below. Rather than smoothing that away:

- _[caveat 1 from README's "Known caveats" section — copy the ones that turned out to matter]_
- _[note any platform whose free tier wasn't actually resource-equivalent to the others]_
- _[note any query-language limitation that changed what a "1-hop traversal" meant on that platform]_

## Try it yourself

The full harness — five platform adapters behind one `GraphPlatform` interface, one command per
platform, a generated results matrix — is open source: **[repo link]**. Bring your own free-tier
accounts and `mvn exec:java -Dexec.args=run-all` reproduces every number above.
