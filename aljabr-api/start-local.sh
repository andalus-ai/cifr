#!/usr/bin/env bash
set -e
export GGUF_CONTEXT_SIZE=8192


# ==============================================================================
# Aljabr (Andalus) Backend Dev Server Launcher
# ==============================================================================

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "=== Starting Aljabr Agent & Substrate Backend ==="
echo "Working directory: $SCRIPT_DIR"
echo "Listening on: HTTP 8085, gRPC 9000"

if [ -z "$JAVA_HOME" ]; then
    if command -v /usr/libexec/java_home >/dev/null 2>&1; then
        export JAVA_HOME="$(/usr/libexec/java_home 2>/dev/null)"
    fi
fi

export PATH="/opt/homebrew/bin:/opt/homebrew/sbin:/usr/local/bin:$HOME/.local/bin:$PATH"

if [ -n "$JAVA_HOME" ] && [ -d "$JAVA_HOME/bin" ]; then
    export PATH="$JAVA_HOME/bin:$PATH"
fi

# Clean up any stale process holding ports 8085 or 9000
for port in 8085 9000; do
    PID=$(lsof -ti :$port -sTCP:LISTEN 2>/dev/null || true)
    if [ -n "$PID" ]; then
        echo "Reclaiming port $port from existing process $PID..."
        kill -9 $PID 2>/dev/null || true
        sleep 1
    fi
done

RUNNER_JAR="$SCRIPT_DIR/target/quarkus-app/quarkus-run.jar"
if [ -f "$RUNNER_JAR" ]; then
    echo "Launching via packaged runner: $RUNNER_JAR"
    exec java -Dquarkus.http.port=8085 -Dquarkus.grpc.server.port=9000 -jar "$RUNNER_JAR"
else
    echo "Launching via Quarkus dev mode..."
    exec mvn quarkus:dev -Dquarkus.http.port=8085
fi
