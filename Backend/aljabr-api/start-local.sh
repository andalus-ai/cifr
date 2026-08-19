#!/usr/bin/env bash
set -e

# ==============================================================================
# Aljabr (Wayang) Backend Dev Server Launcher
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

if [ -n "$JAVA_HOME" ] && [ -d "$JAVA_HOME/bin" ]; then
    export PATH="$JAVA_HOME/bin:$PATH"
fi

exec mvn quarkus:dev -o -Dquarkus.http.port=8085
