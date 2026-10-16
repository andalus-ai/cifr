#!/usr/bin/env bash
set -e

# ==============================================================================
# Gollek Local Inference Server Launcher (Pure Java)
# Starts the Gollek OpenAI-compatible inference API on port 8080.
# ==============================================================================

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
if [ -d "$SCRIPT_DIR/../../../../../Families/gollek" ]; then
    WORKSPACE_ROOT="$(cd "$SCRIPT_DIR/../../../../../" && pwd)"
elif [ -d "$SCRIPT_DIR/../../../../Families/gollek" ]; then
    WORKSPACE_ROOT="$(cd "$SCRIPT_DIR/../../../../" && pwd)"
elif [ -n "$ANDALUS_HOME" ] && [ -d "$ANDALUS_HOME/Families/gollek" ]; then
    WORKSPACE_ROOT="$ANDALUS_HOME"
else
    WORKSPACE_ROOT="$(cd "$SCRIPT_DIR/../../../../../" && pwd)"
fi
GOLLEK_DIR="$WORKSPACE_ROOT/Families/gollek"
GOLLEK_API_JAR="$GOLLEK_DIR/ui/gollek-api/build/quarkus-app/quarkus-run.jar"
GOLLEK_CLI_JAR="$GOLLEK_DIR/ui/gollek-cli/build/gollek.jar"

export PATH="/opt/homebrew/bin:/opt/homebrew/sbin:/usr/local/bin:$HOME/.local/bin:$PATH"

echo "=== Starting Gollek Local Inference Server ==="
echo "Gollek root: $GOLLEK_DIR"
echo "Listening on: HTTP 8080 / OpenAI API /v1"

if [ ! -d "$GOLLEK_DIR" ]; then
    echo "[ERROR] Gollek directory not found at: $GOLLEK_DIR" >&2
    exit 1
fi

# Resolve JAVA_HOME
if [ -z "${JAVA_HOME:-}" ] && command -v /usr/libexec/java_home >/dev/null 2>&1; then
    JAVA_25_HOME="$(/usr/libexec/java_home -v 25 2>/dev/null || true)"
    if [ -n "$JAVA_25_HOME" ]; then
        export JAVA_HOME="$JAVA_25_HOME"
    else
        export JAVA_HOME="$(/usr/libexec/java_home 2>/dev/null)"
    fi
fi

if [ -n "${JAVA_HOME:-}" ] && [ -d "$JAVA_HOME/bin" ]; then
    export PATH="$JAVA_HOME/bin:$PATH"
fi

# Ensure JVM flags required by Gollek (vector incubator + native access)
export JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:-} --enable-native-access=ALL-UNNAMED --add-modules=jdk.incubator.vector -XX:MaxDirectMemorySize=24g"

cd "$GOLLEK_DIR"

# If already listening on port 8080, inform and exit cleanly
if lsof -i :8080 -sTCP:LISTEN >/dev/null 2>&1; then
    EXISTING_PID=$(lsof -t -i :8080 -sTCP:LISTEN | head -n 1)
    echo "ℹ️  Gollek server is already active and listening on port 8080 (PID: $EXISTING_PID)."
    exit 0
fi
if [ -f "$GOLLEK_API_JAR" ]; then
    echo ":) Launching Gollek API server: $GOLLEK_API_JAR"
    exec java $JAVA_TOOL_OPTIONS -Dquarkus.http.port=8080 -Dquarkus.grpc.server.port=9131 -jar "$GOLLEK_API_JAR"
fi

# 2. If gollek-cli jar exists, use `serve` command
if [ -f "$GOLLEK_CLI_JAR" ]; then
    echo ":) Launching via Gollek CLI serve: $GOLLEK_CLI_JAR"
    exec java $JAVA_TOOL_OPTIONS -jar "$GOLLEK_CLI_JAR" serve --rest --port=8080 --enable-cpu
fi

# 3. If neither jar exists, start in dev mode via Gradle
if [ -f "./gradlew" ]; then
    echo ":) Starting Gollek API via Gradle quarkusDev..."
    exec ./gradlew :ui:gollek-api:quarkusDev --console=plain
fi

echo "[ERROR] Could not start Gollek server." >&2
exit 1
