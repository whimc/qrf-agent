#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
JDK_HOME="${ROOT}/.jdk/jdk-21.0.11+10/Contents/Home"

if [[ ! -x "${JDK_HOME}/bin/java" ]]; then
  echo "Portable JDK 21 not found at ${JDK_HOME}"
  echo "Download Temurin 21 for macOS aarch64 into ${ROOT}/.jdk/ or install Java 21 and set JAVA_HOME."
  exit 1
fi

export JAVA_HOME="${JDK_HOME}"
cd "${ROOT}"
chmod +x mvnw
./mvnw -B -ntp -DskipTests package

echo
echo "Built: ${ROOT}/target/WHIMC-QRF-Agent-$(./mvnw -q -DforceStdout help:evaluate -Dexpression=project.version).jar"
echo "Copy that JAR into your Paper server's plugins/ folder and restart."
