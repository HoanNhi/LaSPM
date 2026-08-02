#!/usr/bin/env bash
set -euo pipefail

CALLER_DIR="$(pwd)"
CONFIG_FILE="${CONFIG_FILE:-}"
if [[ -n "${CONFIG_FILE}" && "${CONFIG_FILE}" != /* ]]; then
  CONFIG_FILE="${CALLER_DIR}/${CONFIG_FILE}"
fi

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd -- "${SCRIPT_DIR}/.." && pwd)"
cd "${PROJECT_ROOT}"

TIME_LIMIT="${TIME_LIMIT:-168h}"
KILL_AFTER="${KILL_AFTER:-30s}"
JAVA_HEAP="${JAVA_HEAP:-250G}"
MEMORY_SAMPLE_MILLIS="${MEMORY_SAMPLE_MILLIS:-1000}"
CONFIG_FILE="${CONFIG_FILE:-config/laspm.properties}"

MAVEN_ARGS=(exec:java -Dexec.mainClass="LaSPM.Main_batch")
if [[ -n "${CONFIG_FILE}" ]]; then
  if [[ ! -f "${CONFIG_FILE}" ]]; then
    echo "Configuration file does not exist: ${CONFIG_FILE}" >&2
    exit 2
  fi
  MAVEN_ARGS=(-DLaSPM.config="${CONFIG_FILE}" "${MAVEN_ARGS[@]}")
fi

mvn clean compile

echo "Starting LaSPM.Main_batch with timeout ${TIME_LIMIT}"
echo "Java heap: ${JAVA_HEAP}"
echo "Memory sample interval: ${MEMORY_SAMPLE_MILLIS} ms"
if [[ -n "${CONFIG_FILE}" ]]; then
  echo "Configuration file: ${CONFIG_FILE}"
fi
echo "---- $(date)"

set +e
MAVEN_OPTS="-Xmx${JAVA_HEAP} -DLaSPM.memorySampleMillis=${MEMORY_SAMPLE_MILLIS}" \
  timeout --kill-after="${KILL_AFTER}" "${TIME_LIMIT}" \
  mvn "${MAVEN_ARGS[@]}"
status=$?
set -e

echo "---- $(date)"

if [ "${status}" -eq 124 ]; then
  echo "LaSPM.Main_batch exceeded ${TIME_LIMIT} and was stopped."
elif [ "${status}" -eq 137 ]; then
  echo "LaSPM.Main_batch exceeded ${TIME_LIMIT} and was force-killed after ${KILL_AFTER}."
else
  echo "LaSPM.Main_batch finished with exit code ${status}."
fi

echo "Memory summary is written by Main_batch to:"
echo "  <outputFolder>/batch_memory_summary.csv"
echo "  <outputFolder>/ablation/<mode>/batch_memory_summary.csv when Settings.ablation=true"

exit "${status}"
