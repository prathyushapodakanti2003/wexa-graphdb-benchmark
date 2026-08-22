# Downloads the MUSAE Facebook Large Page-Page Network edge list into data/.
# Equivalent to `mvn exec:java -Dexec.args=download`, kept as a standalone
# script for anyone who wants to prep the data without building the Java
# project. SNAP's own download link for this dataset is dead ("Not
# available"), so this mirrors from the original paper authors' repo -
# verified to match SNAP's stated 171,002-edge count. Cite Rozemberczki et
# al. 2019 per https://snap.stanford.edu/data/facebook-large-page-page-network.html

$ErrorActionPreference = "Stop"
$edgesUrl = "https://raw.githubusercontent.com/benedekrozemberczki/MUSAE/master/input/edges/facebook_edges.csv"
$dataDir = Join-Path $PSScriptRoot "..\data"

New-Item -ItemType Directory -Force -Path $dataDir | Out-Null
Write-Host "Downloading $edgesUrl ..."
Invoke-WebRequest -Uri $edgesUrl -OutFile (Join-Path $dataDir "musae_facebook_edges.csv")

Write-Host "Done: $(Join-Path $dataDir 'musae_facebook_edges.csv')"
