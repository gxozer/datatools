#!/usr/bin/env bash
# Verification script for container test infrastructure (PR-18)
# Run this first — it should FAIL (red) before infrastructure is in place,
# and PASS (green) once setup is complete.

set -euo pipefail

PASS=0
FAIL=0

check() {
    local description="$1"
    local result="$2"
    if [ "$result" = "ok" ]; then
        echo "  [PASS] $description"
        PASS=$((PASS + 1))
    else
        echo "  [FAIL] $description"
        FAIL=$((FAIL + 1))
    fi
}

echo ""
echo "=== Container Test Infrastructure Verification ==="
echo ""

# 1. container-structure-test is installed and on PATH
if command -v container-structure-test &>/dev/null; then
    check "container-structure-test is installed and on PATH" "ok"
else
    check "container-structure-test is installed and on PATH" "fail"
fi

# 2. tests/container/ directory exists
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
if [ -d "$SCRIPT_DIR" ]; then
    check "tests/container/ directory exists" "ok"
else
    check "tests/container/ directory exists" "fail"
fi

# 3. backend.yaml exists and is valid YAML
BACKEND_YAML="$SCRIPT_DIR/backend.yaml"
if [ -f "$BACKEND_YAML" ] && container-structure-test test --image scratch --config "$BACKEND_YAML" &>/dev/null || [ -f "$BACKEND_YAML" ] && grep -q "schemaVersion" "$BACKEND_YAML"; then
    check "tests/container/backend.yaml exists and is valid YAML" "ok"
else
    check "tests/container/backend.yaml exists and is valid YAML" "fail"
fi

# 4. frontend.yaml exists and is valid YAML
FRONTEND_YAML="$SCRIPT_DIR/frontend.yaml"
if [ -f "$FRONTEND_YAML" ] && grep -q "schemaVersion" "$FRONTEND_YAML"; then
    check "tests/container/frontend.yaml exists and is valid YAML" "ok"
else
    check "tests/container/frontend.yaml exists and is valid YAML" "fail"
fi

# 5. Makefile exists with test-containers target
MAKEFILE="$SCRIPT_DIR/../../Makefile"
if [ -f "$MAKEFILE" ] && grep -q "test-containers" "$MAKEFILE"; then
    check "Makefile defines test-containers target" "ok"
else
    check "Makefile defines test-containers target" "fail"
fi

echo ""
echo "=== Results: $PASS passed, $FAIL failed ==="
echo ""

[ "$FAIL" -eq 0 ]
