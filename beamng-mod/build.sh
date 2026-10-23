#!/usr/bin/env sh
# Packages the BeamNG mod as gamecontroller.zip (mod files at the zip root, as BeamNG expects).
# Usage: sh build.sh [output-dir]   (default: ./dist)
set -eu
here="$(cd "$(dirname "$0")" && pwd)"
out="${1:-$here/dist}"
mkdir -p "$out"
out="$(cd "$out" && pwd)"
rm -f "$out/gamecontroller.zip"
cd "$here/gamecontroller"
zip -r -X -q "$out/gamecontroller.zip" mod_info scripts lua
echo "Wrote $out/gamecontroller.zip"
