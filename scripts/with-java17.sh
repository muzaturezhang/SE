#!/usr/bin/env sh
set -eu
project_dir=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
local_jdk="$project_dir/.cache/jdk17/zulu17.70.113-ca-jdk17.0.21.0.3-macosx_aarch64/Contents/Home"
if [ -n "${JAVA17_HOME:-}" ]; then
  selected_jdk=$JAVA17_HOME
elif [ -x "$local_jdk/bin/java" ]; then
  selected_jdk=$local_jdk
elif [ -n "${JAVA_HOME:-}" ]; then
  selected_jdk=$JAVA_HOME
elif [ -x /usr/libexec/java_home ]; then
  selected_jdk=$(/usr/libexec/java_home -v 17 2>/dev/null || true)
else
  selected_jdk=""
fi
if [ ! -x "$selected_jdk/bin/java" ]; then
  printf '%s\n' 'Set JAVA17_HOME to a JDK 17 installation, then run this script again.' >&2
  exit 1
fi
version_line=$("$selected_jdk/bin/java" -version 2>&1 | head -n 1)
case "$version_line" in
  *'version "17.'*) ;;
  *) printf '%s\n' 'The selected runtime is not Java 17. Set JAVA17_HOME explicitly.' >&2; exit 1 ;;
esac
if [ "$#" -eq 0 ]; then
  printf '%s\n' 'Usage: ./scripts/with-java17.sh ./scripts/test.sh (or ./scripts/dev-ui.sh)' >&2
  exit 1
fi
export JAVA_HOME="$selected_jdk"
export PATH="$JAVA_HOME/bin:$PATH"
exec "$@"
