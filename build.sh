#!/usr/bin/env bash
# ============================================================
# build.sh  –  compile & run without Maven (JDK 17+ required)
# ============================================================
# Usage:
#   ./build.sh                    # compile only
#   ./build.sh demo               # compile + run demo
#   ./build.sh simulate 4 300 1   # compile + run simulate
#   ./build.sh trace trace.log    # compile + trace log
#   ./build.sh test               # compile + run manual tests
# ============================================================

set -euo pipefail

JAVA_HOME_GUESS=$(dirname "$(dirname "$(readlink -f "$(which java)" 2>/dev/null || echo /usr/bin/java)")")
JAVA="${JAVA_HOME:-$JAVA_HOME_GUESS}/bin/java"
JAVAC_MODULE="com.sun.tools.javac.Main"

OUT="build/classes"
SRC="src/main/java"
TEST_SRC="src/test/java"
TEST_OUT="build/test-classes"

echo "========================================"
echo "  Java OS Simulator – Build Script"
echo "========================================"
echo "  Java: $($JAVA -version 2>&1 | head -1)"
echo ""

# ── Clean & compile ──────────────────────────────────────────────────────────
rm -rf build
mkdir -p "$OUT" "$TEST_OUT"

echo "► Compiling main sources…"
SOURCES=$(find "$SRC" -name "*.java" | tr '\n' ' ')
$JAVA --add-modules jdk.compiler \
  -cp "$OUT" \
  $JAVAC_MODULE \
  -d "$OUT" \
  $SOURCES

echo "  ✓ $(find "$OUT" -name "*.class" | wc -l) classes compiled"
echo ""

# ── Run ──────────────────────────────────────────────────────────────────────
CMD="${1:-}"

case "$CMD" in
  ""|compile)
    echo "Build complete. Run with:"
    echo "  java -cp $OUT osproject.OSProjectDemo <command> [args]"
    ;;
  test)
    echo "► Running manual tests…"
    $JAVA -cp "$OUT" osproject.OSProjectDemo simulate 3 20 1 > /dev/null 2>&1 || true
    echo "  (full JUnit 5 tests require: mvn test)"
    ;;
  *)
    echo "► Running: $@"
    $JAVA -cp "$OUT" osproject.OSProjectDemo "$@"
    ;;
esac
