package ink.garry.rd.agent.ws.infra.common.util;

/**
 * Snowflake ID 生成器（Twitter 算法）。
 * <p>
 * 64 bit 结构：1 符号位 + 41 时间戳 + 10 worker_id + 12 sequence
 * 单实例每秒可产生 4096*1000 = 4M ID。
 */
public class Snowflake {

    /** 起始时间戳 2026-04-01 00:00:00 UTC+8 */
    private static final long EPOCH = 1774915200000L;

    private static final long WORKER_BITS = 10L;
    private static final long SEQUENCE_BITS = 12L;
    private static final long MAX_WORKER = -1L ^ (-1L << WORKER_BITS);
    private static final long MAX_SEQUENCE = -1L ^ (-1L << SEQUENCE_BITS);
    private static final long WORKER_SHIFT = SEQUENCE_BITS;
    private static final long TIMESTAMP_SHIFT = SEQUENCE_BITS + WORKER_BITS;

    private final long workerId;
    private long sequence = 0L;
    private long lastTimestamp = -1L;

    public Snowflake(long workerId) {
        if (workerId < 0 || workerId > MAX_WORKER) {
            throw new IllegalArgumentException("workerId must be 0~" + MAX_WORKER);
        }
        this.workerId = workerId;
    }

    public synchronized long nextId() {
        long timestamp = System.currentTimeMillis();
        if (timestamp < lastTimestamp) {
            throw new IllegalStateException("Clock moved backwards by " + (lastTimestamp - timestamp) + " ms");
        }
        if (timestamp == lastTimestamp) {
            sequence = (sequence + 1) & MAX_SEQUENCE;
            if (sequence == 0L) {
                timestamp = waitNextMillis(lastTimestamp);
            }
        } else {
            sequence = 0L;
        }
        lastTimestamp = timestamp;
        return ((timestamp - EPOCH) << TIMESTAMP_SHIFT) | (workerId << WORKER_SHIFT) | sequence;
    }

    private long waitNextMillis(long lastTs) {
        long ts = System.currentTimeMillis();
        while (ts <= lastTs) {
            ts = System.currentTimeMillis();
        }
        return ts;
    }
}
