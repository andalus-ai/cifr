#!/usr/bin/env bash
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

echo "=== Launching Dual Backends: Cifr Agent Platform & Gollek Inference Engine ==="

"$SCRIPT_DIR/start-gollek.sh" &
GOLLEK_PID=$!

"$SCRIPT_DIR/cifr-api/start-local.sh" &
CIFR_PID=$!

trap "kill $GOLLEK_PID $CIFR_PID 2>/dev/null || true; exit" SIGINT SIGTERM EXIT
wait
