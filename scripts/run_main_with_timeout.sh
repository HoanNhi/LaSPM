#!/usr/bin/env bash
set -euo pipefail

mvn clean compile

TIME_LIMIT="${TIME_LIMIT:-168h}"
KILL_AFTER="${KILL_AFTER:-30s}"
MAIN_CONFIG_FILE="${MAIN_CONFIG_FILE:-}"

MAVEN_ARGS=(exec:java -Dexec.mainClass="LaSPM.Main")
if [[ -n "${MAIN_CONFIG_FILE}" ]]; then
  if [[ ! -f "${MAIN_CONFIG_FILE}" ]]; then
    echo "Configuration file does not exist: ${MAIN_CONFIG_FILE}" >&2
    exit 2
  fi
  MAVEN_ARGS=(-DLaSPM.mainConfig="${MAIN_CONFIG_FILE}" "${MAVEN_ARGS[@]}")
fi

echo "Starting LaSPM.Main with timeout ${TIME_LIMIT}"
if [[ -n "${MAIN_CONFIG_FILE}" ]]; then
  echo "Configuration file: ${MAIN_CONFIG_FILE}"
fi
echo "---- $(date)"

set +e
MAVEN_OPTS="-Xmx250G" timeout --kill-after="${KILL_AFTER}" "${TIME_LIMIT}" \
  mvn "${MAVEN_ARGS[@]}"
status=$?
set -e

echo "---- $(date)"

if [ "${status}" -eq 124 ]; then
  echo "LaSPM.Main exceeded ${TIME_LIMIT} and was stopped."
elif [ "${status}" -eq 137 ]; then
  echo "LaSPM.Main exceeded ${TIME_LIMIT} and was force-killed after ${KILL_AFTER}."
else
  echo "LaSPM.Main finished with exit code ${status}."
fi

exit "${status}"
