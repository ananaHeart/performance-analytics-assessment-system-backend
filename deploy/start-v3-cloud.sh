#!/bin/sh
set -eu
if [ "${SPRING_PROFILES_ACTIVE:-}" != 'v3,v3-mobile-release,v3-cloud' ]; then
    echo 'Use SPRING_PROFILES_ACTIVE=v3,v3-mobile-release,v3-cloud for this cloud image.' >&2
    exit 1
fi
# Point this at the mounted permanent disk; do not store evidence in /app or /tmp.
: "${V3_CLOUD_STORAGE_ROOT:?Set V3_CLOUD_STORAGE_ROOT to the permanent disk mount path}"
if ! mountpoint -q /var/data; then
    echo 'Mount permanent storage at /var/data before starting V3 cloud.' >&2
    exit 1
fi
case "$V3_CLOUD_STORAGE_ROOT" in
    */../*|*/..|*/./*|*/.) echo 'Storage path cannot contain parent or dot segments.' >&2; exit 1 ;;
esac
case "$V3_CLOUD_STORAGE_ROOT" in
    /var/data|/var/data/*) ;;
    *) echo 'V3 cloud storage must be inside the /var/data permanent disk mount.' >&2; exit 1 ;;
esac
mkdir -p "$V3_CLOUD_STORAGE_ROOT/answer-sheets" "$V3_CLOUD_STORAGE_ROOT/scan-evidence"
test -w "$V3_CLOUD_STORAGE_ROOT/answer-sheets"
test -w "$V3_CLOUD_STORAGE_ROOT/scan-evidence"
# JAVA_TOOL_OPTIONS may supply JVM memory limits; no database or schema commands run here.
exec java -jar /app/app.jar
