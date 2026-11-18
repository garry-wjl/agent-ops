#!/bin/sh
# 容器启动入口。堆走 JVM 的 RAM 百分比；元空间和直接内存的开关只接受字节，
# 这里按同一块内存限额换算成百分比后再传给 java。
#
# 内存限额优先读 cgroup（与 Java UseContainerSupport 一致），读不到再用 MemTotal。
# 可用环境变量覆盖百分比：METASPACE_INIT_PERCENT / METASPACE_MAX_PERCENT / DIRECT_MEMORY_PERCENT。
#
# 测试时：JVM_ENTRYPOINT_LIB=1 . ./jvm-entrypoint.sh  只加载函数，不启动 JVM。

set -eu

# 堆：相对容器/主机可用内存的百分比。原先若写 -Xmx 会被容器限额截断或浪费。
HEAP_INITIAL_PERCENT="${HEAP_INITIAL_PERCENT:-40.0}"
HEAP_MIN_PERCENT="${HEAP_MIN_PERCENT:-40.0}"
HEAP_MAX_PERCENT="${HEAP_MAX_PERCENT:-80.0}"

# 元空间：原先 MetaspaceSize=128m、MaxMetaspaceSize=256m。
# 初始值约为上限的一半，上限约占限额的 8%，给 80% 的堆留下非堆空间。
METASPACE_INIT_PERCENT="${METASPACE_INIT_PERCENT:-4}"
METASPACE_MAX_PERCENT="${METASPACE_MAX_PERCENT:-8}"

# 直接内存：不设置时 JVM 默认等于最大堆（再占一份 80%）。改成限额的百分比。
DIRECT_MEMORY_PERCENT="${DIRECT_MEMORY_PERCENT:-6}"

# 返回内存限额字节数。失败时打印到 stderr 并返回非 0。
jvm_memory_limit_bytes() {
    if [ -r /sys/fs/cgroup/memory.max ]; then
        v=$(cat /sys/fs/cgroup/memory.max)
        if [ -n "$v" ] && [ "$v" != "max" ]; then
            echo "$v"
            return 0
        fi
    fi
    if [ -r /sys/fs/cgroup/memory/memory.limit_in_bytes ]; then
        v=$(cat /sys/fs/cgroup/memory/memory.limit_in_bytes)
        # cgroup v1 无限制时是一个接近 2^63 的哨兵值
        if [ -n "$v" ] && [ "$v" -lt 4611686018427387904 ] 2>/dev/null; then
            echo "$v"
            return 0
        fi
    fi
    if [ -r /proc/meminfo ]; then
        awk '/^MemTotal:/ { printf "%d\n", $2 * 1024; found = 1 } END { exit !found }' /proc/meminfo
        return $?
    fi
    echo "jvm-entrypoint: 读不到内存限额" >&2
    return 1
}

# $1 限额字节，$2 整数百分比。向下取整。
jvm_percent_bytes() {
    echo $(( $1 * $2 / 100 ))
}

if [ -n "${JVM_ENTRYPOINT_LIB:-}" ]; then
    return 0 2>/dev/null || exit 0
fi

limit=$(jvm_memory_limit_bytes)
meta_init=$(jvm_percent_bytes "$limit" "$METASPACE_INIT_PERCENT")
meta_max=$(jvm_percent_bytes "$limit" "$METASPACE_MAX_PERCENT")
direct_max=$(jvm_percent_bytes "$limit" "$DIRECT_MEMORY_PERCENT")
if [ "$meta_init" -gt "$meta_max" ]; then
    meta_init=$meta_max
fi

echo "jvm-entrypoint: limit=${limit} heap=${HEAP_INITIAL_PERCENT}/${HEAP_MIN_PERCENT}/${HEAP_MAX_PERCENT}% metaspace=${meta_init}/${meta_max} direct=${direct_max}" >&2

exec java \
    --add-opens=java.base/java.lang=ALL-UNNAMED \
    -Duser.timezone=Asia/Shanghai \
    -Dfile.encoding=UTF-8 \
    -Dclient.encoding.override=UTF-8 \
    -Djava.security.egd=file:/dev/./urandom \
    -XX:InitialRAMPercentage="${HEAP_INITIAL_PERCENT}" \
    -XX:MinRAMPercentage="${HEAP_MIN_PERCENT}" \
    -XX:MaxRAMPercentage="${HEAP_MAX_PERCENT}" \
    -XX:MetaspaceSize="${meta_init}" \
    -XX:MaxMetaspaceSize="${meta_max}" \
    -XX:MaxDirectMemorySize="${direct_max}" \
    -XX:+UseG1GC \
    -XX:MaxGCPauseMillis=200 \
    -XX:+ExplicitGCInvokesConcurrent \
    -XX:-OmitStackTraceInFastThrow \
    -XX:+HeapDumpOnOutOfMemoryError \
    -XX:HeapDumpPath=/app/heapdump/heapdump.hprof \
    -Xlog:gc*:file=/app/logs/gc.log:time,uptime,level,tags \
    -jar /app/app.jar
