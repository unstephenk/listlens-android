#!/usr/bin/env bash
set -euo pipefail

# ListLens container bootstrap (Android SDK + JDK)
#
# Goal: make this repo buildable inside the OpenClaw workspace container.
# We intentionally do NOT commit SDK/JDK binaries to git.
#
# Default strategy:
# - If a sibling repo (../birch) contains repo-local .sdk/.jdk, symlink to them.
# - Otherwise, fail with instructions.
#
# After running, you should be able to:
#   ./gradlew :app:assembleDebug

APP_HOME="$(cd "$(dirname "$0")/.." && pwd)"
BIRCH_HOME="$(cd "$APP_HOME/../birch" 2>/dev/null && pwd || true)"

say() { echo "[setup-container-android] $*"; }

die() {
  echo "[setup-container-android] ERROR: $*" >&2
  exit 1
}

if [[ -z "${BIRCH_HOME:-}" || ! -d "$BIRCH_HOME" ]]; then
  die "Expected sibling repo at ../birch with .sdk/.jdk. Not found.\n\nIf you want a different source, edit this script or create $APP_HOME/.sdk and $APP_HOME/.jdk manually."
fi

if [[ ! -x "$BIRCH_HOME/.jdk/bin/java" ]]; then
  die "Found ../birch but missing executable JDK at $BIRCH_HOME/.jdk/bin/java"
fi

if [[ ! -d "$BIRCH_HOME/.sdk" ]]; then
  die "Found ../birch but missing Android SDK at $BIRCH_HOME/.sdk"
fi

cd "$APP_HOME"

say "Linking repo-local .jdk and .sdk -> ../birch"
ln -sfn "../birch/.jdk" .jdk
ln -sfn "../birch/.sdk" .sdk

say "Writing local.properties (sdk.dir=$APP_HOME/.sdk)"
cat > local.properties <<EOF
sdk.dir=$APP_HOME/.sdk
EOF

say "Done. Sanity checks:"
"$APP_HOME/.jdk/bin/java" -version | head -n 1 || true
if [[ -x "$APP_HOME/.sdk/platform-tools/adb" ]]; then
  say "adb: $($APP_HOME/.sdk/platform-tools/adb version | head -n 1)"
else
  say "adb not found at .sdk/platform-tools/adb (this is OK if you only build in-container)."
fi

say "Next: ./gradlew :app:assembleDebug"
