#!/bin/sh
set -e
VERSION="${VERSION:-0.1.0}"
BASE=https://raw.githubusercontent.com/alexcastrodev/swmm4j/refs/heads/main/examples

mkdir -p out
cd out
curl -fsSLO "$BASE/Example7.inp"
curl -fsSLO "$BASE/Example7.csv"
docker pull -q "ghcr.io/alexcastrodev/swmm4j:$VERSION"
docker run --rm -v "$PWD:/work" "ghcr.io/alexcastrodev/swmm4j:$VERSION" --inp Example7.inp --csv Example7.csv --dir .
