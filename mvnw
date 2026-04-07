#!/bin/sh
# ----------------------------------------------------------------------------
# Maven Wrapper shell script, version 3.3.2
# ----------------------------------------------------------------------------

set -euf

BASE_DIR="$(cd "$(dirname "$0")" && pwd)"
WRAPPER_JAR="$BASE_DIR/.mvn/wrapper/maven-wrapper.jar"
WRAPPER_PROPERTIES="$BASE_DIR/.mvn/wrapper/maven-wrapper.properties"

if [ ! -f "$WRAPPER_PROPERTIES" ]; then
  echo "Missing $WRAPPER_PROPERTIES" >&2
  exit 1
fi

WRAPPER_URL="$(grep -E '^wrapperUrl=' "$WRAPPER_PROPERTIES" | sed 's/^wrapperUrl=//')"

if [ ! -f "$WRAPPER_JAR" ]; then
  if [ -z "${WRAPPER_URL:-}" ]; then
    echo "Missing wrapperUrl in $WRAPPER_PROPERTIES" >&2
    exit 1
  fi
  mkdir -p "$BASE_DIR/.mvn/wrapper"
  echo "Downloading Maven wrapper from $WRAPPER_URL"
  if command -v curl >/dev/null 2>&1; then
    curl -fsSL "$WRAPPER_URL" -o "$WRAPPER_JAR"
  elif command -v wget >/dev/null 2>&1; then
    wget -q "$WRAPPER_URL" -O "$WRAPPER_JAR"
  else
    echo "Neither curl nor wget found; cannot download wrapper jar." >&2
    exit 1
  fi
fi

JAVA_EXE="${JAVA_HOME:-}/bin/java"
if [ ! -x "$JAVA_EXE" ]; then
  JAVA_EXE="java"
fi

exec "$JAVA_EXE" -classpath "$WRAPPER_JAR" -Dmaven.multiModuleProjectDirectory="$BASE_DIR" org.apache.maven.wrapper.MavenWrapperMain "$@"

