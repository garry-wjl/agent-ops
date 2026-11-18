#!/bin/sh
# 校验百分比换算：固定 128m/256m 换成限额的百分比后，大机器的元空间上限应高于旧的 256m。
set -eu
cd "$(dirname "$0")"
JVM_ENTRYPOINT_LIB=1
# shellcheck disable=SC1091
. ./jvm-entrypoint.sh

fail() {
    echo "FAIL: $1" >&2
    exit 1
}

got=$(jvm_percent_bytes 1000 8)
[ "$got" = "80" ] || fail "1000*8% expected 80, got $got"

one_gi=$((1024 * 1024 * 1024))
got=$(jvm_percent_bytes "$one_gi" 8)
[ "$got" = "85899345" ] || fail "1Gi*8% expected 85899345, got $got"

four_gi=$((4 * one_gi))
meta_max=$(jvm_percent_bytes "$four_gi" 8)
old_cap=$((256 * 1024 * 1024))
[ "$meta_max" -gt "$old_cap" ] || fail "8% of 4Gi ($meta_max) should exceed old 256m cap ($old_cap)"

half_gi=$((one_gi / 2))
small=$(jvm_percent_bytes "$half_gi" 8)
[ "$small" -lt "$old_cap" ] || fail "8% of 512Mi ($small) should stay under old 256m cap"

echo "jvm-entrypoint percentage checks ok"
