#!/usr/bin/env bash
# Builds the overlay proof page: packages the component library, then compiles the proof page
# and the library it uses to JavaScript with TeaVM. Output lands in web/js/classes.js.
#
# Run it from anywhere; it finds the checkout from its own location.
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$HERE/../.." && pwd)"

export JAVA_HOME="${JAVA_HOME_21:-C:/Program Files/Eclipse Adoptium/jdk-21.0.7.6-hotspot}"
export PATH="$JAVA_HOME/bin:$PATH"

echo "== packaging the component library =="
(cd "$ROOT" && mvn -o -q -pl zerozstack-ui-components -am package -DskipTests)

echo "== staging jars =="
mkdir -p "$HERE/lib"
cp "$ROOT"/zerozstack-ui-components/target/zerozstack-ui-components-*.jar "$HERE/lib/ui-components.jar"
cp "$ROOT"/zerozstack-shared-api/target/zerozstack-shared-api-*.jar "$HERE/lib/shared-api.jar"

echo "== compiling the proof page to JavaScript =="
(cd "$HERE" && mvn -o -q package)

echo "done: $HERE/web/js/classes.js"
