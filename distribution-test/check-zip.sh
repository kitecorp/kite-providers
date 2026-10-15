#!/usr/bin/env bash
# Runs the distribution test against a provider release zip, unzipped the way a user
# gets it, so the check covers the file that is published rather than the build
# directory it was made from. Arguments after the zip go to Gradle.
#
#   distribution-test/check-zip.sh "$RUNNER_TEMP/azure.zip"
set -euo pipefail

if [ $# -lt 1 ]; then
  echo "usage: $0 <provider.zip> [gradle arguments...]" >&2
  exit 2
fi

ZIP="$(cd "$(dirname "$1")" && pwd)/$(basename "$1")"
shift
DIST="$(mktemp -d)"
trap 'rm -rf "$DIST"' EXIT

unzip -q "$ZIP" -d "$DIST"
cd "$(dirname "$0")"
./gradlew test -PdistributionDirs="$DIST" --no-daemon "$@"
