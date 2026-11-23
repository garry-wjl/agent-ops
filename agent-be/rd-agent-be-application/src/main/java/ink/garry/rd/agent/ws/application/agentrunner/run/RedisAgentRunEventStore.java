package ink.garry.rd.agent.ws.application.agentrunner.run;
import ink.garry.rd.agent.ws.client.common.BizCode;
import ink.garry.rd.agent.ws.facade.exception.BusinessException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBatch;
import org.redisson.api.RBucket;
import org.redisson.api.RMap;
import org.redisson.api.RMapAsync;
import org.redisson.api.RStream;
import org.redisson.api.RStreamAsync;
import org.redisson.api.RedissonClient;
import org.redisson.api.stream.StreamAddArgs;
import org.redisson.api.stream.StreamMessageId;
import org.redisson.api.stream.StreamRangeArgs;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Redis Stream 实现的 Agent run 事件缓冲。
 * <ul>
 *   <li>{@code agent:run:{runId}:meta} — Hash 元数据</li>
 *   <li>{@code agent:run:{runId}:events} — Stream 事件，ID 为 {@code {seq}-0}</li>
 *   <li>{@code agent:session:{sessionNum}:running_run} — 进行中 run 索引</li>
 * </ul>
 * 轮询只读取游标之后的一页，写入用一次 pipeline 完成。单条 run 只保留最近
 * {@link #EVENT_CAP} 条，更早的事件被裁掉后无法续传。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisAgentRunEventStore implements AgentRunEventStore {

    private static final String META_KEY = "agent:run:%s:meta";
    private static final String EVENTS_KEY = "agent:run:%s:events";
    private static final String RUNNING_KEY = "agent:session:%s:running_run";
    /** meta 字段：新 run 用序号作为 Stream ID，读取可以按序号定位 */
    private static final String STREAM_ID_MODE = "streamIdMode";
    private static final String STREAM_ID_SEQ = "seq";
    /** 单条 run 在 Redis 中保留的事件上限，压住过大的环境变量 */
    static final int EVENT_CAP = 50_000;
    /** 一次轮询最多返回的事件数 */
    static final int READ_BATCH = 200;

    private final RedissonClient redissonClient;

    /** 结束后缓冲保留时长（重连窗口） */
    @Value("${app.agent-run.ttl-after-finish:45m}")
    private Duration ttlAfterFinish;

    /** 运行中 key 最长存活（防泄漏） */
    @Value("${app.agent-run.ttl-running:2h}")
    private Duration ttlRunning;

    /**
     * 配置的保留条数。大于 {@link #EVENT_CAP} 时按上限保留，运行本身不因此中断。
     */
    @Value("${app.agent-run.max-events:50000}")
    private long maxEvents;

    @Override
    public void createRun(String runId, String sessionNum, String agentNum) {
        RMap<String, String> meta = redissonClient.getMap(metaKey(runId));
        meta.putAll(Map.of(
                "runId", runId,
                "sessionNum", sessionNum,
                "agentNum", agentNum,
                "status", AgentRunStatus.RUNNING.name(),
                "lastSeq", "0",
                STREAM_ID_MODE, STREAM_ID_SEQ));
        meta.expire(ttlRunning);

        RBucket<String> running = redissonClient.getBucket(runningKey(sessionNum));
        running.set(runId, ttlRunning);

        // 预创建空 stream，便于后续 range
        RStream<String, String> stream = redissonClient.getStream(eventsKey(runId));
        stream.expire(ttlRunning);
        log.info("[AgentRunEventStore] createRun runId={} sessionNum={} agentNum={}",
                runId, sessionNum, agentNum);
    }

    @Override
    public void append(String runId, long seq, String event, String data) {
        RMap<String, String> meta = redissonClient.getMap(metaKey(runId));
        Map<String, String> snap = meta.getAll(Set.of("lastSeq", "sessionNum", STREAM_ID_MODE));
        if (snap.isEmpty()) {
            log.warn("[AgentRunEventStore] append skipped, meta missing runId={} seq={}", runId, seq);
            return;
        }
        long lastSeq = parseLong(snap.get("lastSeq"));
        if (seq <= lastSeq) {
            log.warn("[AgentRunEventStore] non-monotonic seq runId={} seq={} lastSeq={}",
                    runId, seq, lastSeq);
            return;
        }
        int limit = eventLimit();
        if (seq == limit + 1L) {
            log.warn("[AgentRunEventStore] retain only last {} events runId={}", limit, runId);
        }

        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("seq", String.valueOf(seq));
        fields.put("data", data == null ? "{}" : data);
        if (event != null && !event.isBlank()) {
            fields.put("event", event);
        }
        StreamAddArgs<String, String> args = StreamAddArgs.<String, String>entries(fields)
                .trim()
                .maxLen(limit)
                .noLimit();

        RBatch batch = redissonClient.createBatch();
        RStreamAsync<String, String> stream = batch.getStream(eventsKey(runId));
        if (STREAM_ID_SEQ.equals(snap.get(STREAM_ID_MODE))) {
            stream.addAsync(new StreamMessageId(seq, 0), args);
        } else {
            stream.addAsync(args);
        }
        RMapAsync<String, String> metaBatch = batch.getMap(metaKey(runId));
        metaBatch.putAsync("lastSeq", String.valueOf(seq));
        metaBatch.expireAsync(ttlRunning);
        stream.expireAsync(ttlRunning);
        String sessionNum = snap.get("sessionNum");
        if (sessionNum != null && !sessionNum.isBlank()) {
            batch.<String>getBucket(runningKey(sessionNum)).expireAsync(ttlRunning);
        }
        batch.execute();
    }

    @Override
    public void complete(String runId, AgentRunStatus status) {
        RMap<String, String> meta = redissonClient.getMap(metaKey(runId));
        if (!meta.isExists()) {
            return;
        }
        String sessionNum = meta.get("sessionNum");
        meta.put("status", status.name());
        meta.expire(ttlAfterFinish);
        redissonClient.getStream(eventsKey(runId)).expire(ttlAfterFinish);
        if (sessionNum != null) {
            RBucket<String> running = redissonClient.getBucket(runningKey(sessionNum));
            String current = running.get();
            if (runId.equals(current)) {
                running.delete();
            }
        }
        log.info("[AgentRunEventStore] complete runId={} status={}", runId, status);
    }

    @Override
    public Optional<String> findRunningRunId(String sessionNum) {
        if (sessionNum == null || sessionNum.isBlank()) {
            return Optional.empty();
        }
        String runId = redissonClient.<String>getBucket(runningKey(sessionNum)).get();
        return Optional.ofNullable(runId).filter(s -> !s.isBlank());
    }

    @Override
    public Optional<AgentRunMeta> getMeta(String runId) {
        RMap<String, String> meta = redissonClient.getMap(metaKey(runId));
        if (!meta.isExists()) {
            return Optional.empty();
        }
        String statusRaw = meta.get("status");
        AgentRunStatus status;
        try {
            status = AgentRunStatus.valueOf(statusRaw);
        } catch (RuntimeException e) {
            status = AgentRunStatus.FAILED;
        }
        long lastSeq = 0L;
        try {
            lastSeq = Long.parseLong(meta.getOrDefault("lastSeq", "0"));
        } catch (NumberFormatException ignored) {
            // keep 0
        }
        return Optional.of(new AgentRunMeta(
                meta.get("runId"),
                meta.get("sessionNum"),
                meta.get("agentNum"),
                status,
                lastSeq));
    }

    @Override
    public List<AgentRunStreamEvent> listAfter(String runId, long afterSeq) {
        RMap<String, String> meta = redissonClient.getMap(metaKey(runId));
        RStream<String, String> stream = redissonClient.getStream(eventsKey(runId));
        if (STREAM_ID_SEQ.equals(meta.get(STREAM_ID_MODE))) {
            return listAfterSeqId(runId, stream, afterSeq);
        }
        return listAfterFullScan(runId, stream, afterSeq);
    }

    /**
     * 从 {@code afterSeq} 之后取一页。序号被 MAXLEN 裁掉时无法补洞，按游标过期处理。
     */
    private List<AgentRunStreamEvent> listAfterSeqId(
            String runId, RStream<String, String> stream, long afterSeq) {
        StreamRangeArgs args = afterSeq > 0
                ? StreamRangeArgs.startIdExclusive(new StreamMessageId(afterSeq, 0))
                .endId(StreamMessageId.MAX)
                .count(READ_BATCH)
                : StreamRangeArgs.startId(StreamMessageId.MIN)
                .endId(StreamMessageId.MAX)
                .count(READ_BATCH);
        return toEvents(runId, stream.range(args), afterSeq, true);
    }

    /** 升级前已存在的 run 仍用自动 ID，保持整段过滤，直到这些 run 结束。 */
    private List<AgentRunStreamEvent> listAfterFullScan(
            String runId, RStream<String, String> stream, long afterSeq) {
        return toEvents(runId, stream.range(StreamMessageId.MIN, StreamMessageId.MAX), afterSeq, false);
    }

    private List<AgentRunStreamEvent> toEvents(
            String runId,
            Map<StreamMessageId, Map<String, String>> page,
            long afterSeq,
            boolean rejectGap) {
        if (page == null || page.isEmpty()) {
            return List.of();
        }
        List<AgentRunStreamEvent> out = new ArrayList<>();
        for (Map.Entry<StreamMessageId, Map<String, String>> entry : page.entrySet()) {
            long seq = parseLong(entry.getValue().get("seq"));
            if (seq <= afterSeq) {
                continue;
            }
            Map<String, String> fields = entry.getValue();
            out.add(AgentRunStreamEvent.builder()
                    .id(runId + ":" + seq)
                    .seq(seq)
                    .event(fields.get("event"))
                    .data(fields.getOrDefault("data", "{}"))
                    .build());
        }
        out.sort(Comparator.comparingLong(AgentRunStreamEvent::getSeq));
        if (rejectGap && afterSeq > 0 && !out.isEmpty() && out.get(0).getSeq() > afterSeq + 1) {
            throw new BusinessException(
                    BizCode.CURSOR_EXPIRED.getCode(), BizCode.CURSOR_EXPIRED.getMessage());
        }
        return out;
    }

    private int eventLimit() {
        long configured = maxEvents > 0 ? maxEvents : EVENT_CAP;
        return (int) Math.min(configured, EVENT_CAP);
    }

    private static long parseLong(String raw) {
        if (raw == null || raw.isBlank()) {
            return 0L;
        }
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException ex) {
            return 0L;
        }
    }

    private static String metaKey(String runId) {
        return META_KEY.formatted(runId);
    }

    private static String eventsKey(String runId) {
        return EVENTS_KEY.formatted(runId);
    }

    private static String runningKey(String sessionNum) {
        return RUNNING_KEY.formatted(sessionNum);
    }
}
