package ink.garry.rd.agent.ws.application.agentrunner.run;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBucket;
import org.redisson.api.RMap;
import org.redisson.api.RStream;
import org.redisson.api.RedissonClient;
import org.redisson.api.stream.StreamAddArgs;
import org.redisson.api.stream.StreamMessageId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Redis Stream 实现的 Agent run 事件缓冲。
 * <ul>
 *   <li>{@code agent:run:{runId}:meta} — Hash 元数据</li>
 *   <li>{@code agent:run:{runId}:events} — Stream 事件</li>
 *   <li>{@code agent:session:{sessionNum}:running_run} — 进行中 run 索引</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisAgentRunEventStore implements AgentRunEventStore {

    private static final String META_KEY = "agent:run:%s:meta";
    private static final String EVENTS_KEY = "agent:run:%s:events";
    private static final String RUNNING_KEY = "agent:session:%s:running_run";

    private final RedissonClient redissonClient;

    /** 结束后缓冲保留时长（重连窗口） */
    @Value("${app.agent-run.ttl-after-finish:45m}")
    private Duration ttlAfterFinish;

    /** 运行中 key 最长存活（防泄漏） */
    @Value("${app.agent-run.ttl-running:2h}")
    private Duration ttlRunning;

    /** 单 run 最大事件条数 */
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
                "lastSeq", "0"));
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
        if (!meta.isExists()) {
            log.warn("[AgentRunEventStore] append skipped, meta missing runId={} seq={}", runId, seq);
            return;
        }
        String lastSeqRaw = meta.get("lastSeq");
        long lastSeq = lastSeqRaw == null ? 0L : Long.parseLong(lastSeqRaw);
        if (seq <= lastSeq) {
            log.warn("[AgentRunEventStore] non-monotonic seq runId={} seq={} lastSeq={}", runId, seq, lastSeq);
        }
        if (seq > maxEvents) {
            log.error("[AgentRunEventStore] max-events exceeded runId={} seq={}", runId, seq);
            complete(runId, AgentRunStatus.FAILED);
            return;
        }

        RStream<String, String> stream = redissonClient.getStream(eventsKey(runId));
        Map<String, String> fields = new java.util.LinkedHashMap<>();
        fields.put("seq", String.valueOf(seq));
        fields.put("data", data == null ? "{}" : data);
        if (event != null && !event.isBlank()) {
            fields.put("event", event);
        }
        stream.add(StreamAddArgs.entries(fields));
        meta.put("lastSeq", String.valueOf(seq));
        meta.expire(ttlRunning);
        stream.expire(ttlRunning);
        String sessionNum = meta.get("sessionNum");
        if (sessionNum != null) {
            redissonClient.getBucket(runningKey(sessionNum)).expire(ttlRunning);
        }
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
        RStream<String, String> stream = redissonClient.getStream(eventsKey(runId));
        Map<StreamMessageId, Map<String, String>> all =
                stream.range(StreamMessageId.MIN, StreamMessageId.MAX);
        if (all == null || all.isEmpty()) {
            return List.of();
        }
        List<AgentRunStreamEvent> out = new ArrayList<>();
        for (Map.Entry<StreamMessageId, Map<String, String>> e : all.entrySet()) {
            Map<String, String> fields = e.getValue();
            long seq;
            try {
                seq = Long.parseLong(fields.getOrDefault("seq", "0"));
            } catch (NumberFormatException ex) {
                continue;
            }
            if (seq <= afterSeq) {
                continue;
            }
            out.add(AgentRunStreamEvent.builder()
                    .id(runId + ":" + seq)
                    .seq(seq)
                    .event(fields.get("event"))
                    .data(fields.getOrDefault("data", "{}"))
                    .build());
        }
        out.sort(Comparator.comparingLong(AgentRunStreamEvent::getSeq));
        return out;
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
