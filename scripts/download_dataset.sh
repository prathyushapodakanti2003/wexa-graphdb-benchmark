#!/usr/bin/env bash
# Downloads the MUSAE Facebook Large Page-Page Network edge list into data/.
# Equivalent to `mvn exec:java -Dexec.args=download`, kept as a standalone
# script for anyone who wants to prep the data without building the Java
# project. SNAP's own download link for this dataset is dead ("Not
# available"), so this mirrors from the original paper authors' repo -
# verified to match SNAP's stated 171,002-edge count. Cite Rozemberczki et
# al. 2019 per https://snap.stanford.edu/data/facebook-large-page-page-network.html
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DATA_DIR="$SCRIPT_DIR/../data"
EDGES_URL="https://raw.githubusercontent.com/benedekrozemberczki/MUSAE/master/input/edges/facebook_edges.csv"

mkdir -p "$DATA_DIR"
echo "Downloading $EDGES_URL ..."
curl -fSL "$EDGES_URL" -o "$DATA_DIR/musae_facebook_edges.csv"

echo "Done: $DATA_DIR/musae_facebook_edges.csv"
