#!/bin/sh
# Runs the spec in its container (the SWMM engine and reader for Linux), with this src/ mounted:
#   ./spec.sh                      every test
#   ./spec.sh SwmmOutputTest#json  one class or method
#   ./spec.sh --debug [...]        the tests wait for a debugger on localhost:5005 (IntelliJ: "spec: attach debugger")
set -e
cd "$(dirname "$0")"
debug=""
if [ "$1" = "--debug" ]; then
	debug=1
	shift
fi
docker build -q -f Dockerfile.test -t swmm-cli-test . >/dev/null
# the .m2 volume keeps what Maven fetched between runs
exec docker run --rm -v "$PWD/src:/src/src:ro" -v swmm-cli-m2:/root/.m2 ${debug:+-p 5005:5005} \
	-e DEBUG="$debug" -e TEST="${1:-}" swmm-cli-test
