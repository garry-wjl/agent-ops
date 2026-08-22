package ink.garry.rd.agent.ws.application.evaluation.task;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.TypeReference;
import ink.garry.rd.agent.ws.application.debugconsole.AgentInvokeService;
import ink.garry.rd.agent.ws.application.debugconsole.SegmentAccumulator;
import ink.garry.rd.agent.ws.application.evaluation.dataset.EvalDatasetQueryService;
import ink.garry.rd.agent.ws.application.evaluation.support.GraderBindingSnapshot;
import ink.garry.rd.agent.ws.application.evaluation.support.GraderEngine;
import ink.garry.rd.agent.ws.application.evaluation.support.ScoreResult;
import ink.garry.rd.agent.ws.client.evaluation.dataset.EvalDatasetRowVO;
import ink.garry.rd.agent.ws.client.evaluation.task.EvalTaskItemVO;
import ink.garry.rd.agent.ws.domain.evaluation.task.EvalTask;
import ink.garry.rd.agent.ws.domain.evaluation.task.EvalTaskItem;
import ink.garry.rd.agent.ws.domain.evaluation.task.factory.EvalTaskFactory;
import ink.garry.rd.agent.ws.domain.evaluation.task.factory.EvalTaskItemFactory;
import ink.garry.rd.agent.ws.domain.evaluation.task.valueobject.BindMode;
import ink.garry.rd.agent.ws.domain.evaluation.task.valueobject.EvalItemScore;
import ink.garry.rd.agent.ws.domain.evaluation.task.valueobject.TaskStatus;
import ink.garry.rd.agent.ws.domain.session.valueobject.AssistantSegment;
import ink.garry.rd.agent.ws.infra.common.util.WorkspaceContext;
import ink.garry.rd.agent.ws.infra.common.util.WorkspaceContextHolder;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 评测任务异步 Worker：逐行调用 Agent + 评估器打分；支持多轮 sessionKey 与轨迹摘要。
 */
@Slf4j
@Component
public class EvalTaskWorker {

    @Value("${app.evaluation.task.invoke-timeout-seconds:60}")
    private int invokeTimeoutSeconds;

    @Value("${app.evaluation.task.continue-on-error:true}")
    private boolean continueOnError;

    @Resource
    private EvalTaskFactory evalTaskFactory;
    @Resource
    private EvalTaskItemFactory evalTaskItemFactory;
    @Resource
    private EvalDatasetQueryService evalDatasetQueryService;
    @Resource
    private EvalTaskQueryService evalTaskQueryService;
    @Resource
    private AgentInvokeService agentInvokeService;
    @Resource
    private GraderEngine graderEngine;

    /**
     * 异步跑批。
     */
    @Async("evaluationExecutor")
    public void runAsync(String taskNum, String operatorId) {
        try {
            execute(taskNum, operatorId, false);
        } catch (Exception ex) {
            log.error("EvalTaskWorker failed taskNum={}", taskNum, ex);
            failTaskIfRunning(taskNum, operatorId);
        }
    }

    /**
     * 异步重跑 FAILED/ERROR 用例。
     */
    @Async("evaluationExecutor")
    public void rerunFailedAsync(String taskNum, String operatorId) {
        try {
            execute(taskNum, operatorId, true);
        } catch (Exception ex) {
            log.error("EvalTaskWorker rerunFailed failed taskNum={}", taskNum, ex);
            failTaskIfRunning(taskNum, operatorId);
        }
    }

    private void execute(String taskNum, String operatorId, boolean rerunFailedOnly) {
        EvalTask task = require(taskNum);
        if (task.getStatus() == TaskStatus.CANCELLED) {
            return;
        }
        WorkspaceContextHolder.set(WorkspaceContext.builder()
                .workspaceNum(task.getWorkspaceNum())
                .role("ADMIN")
                .member(true)
                .build());
        try {
            if (rerunFailedOnly) {
                rerunFailedItems(task, taskNum, operatorId);
            } else {
                runAllRows(task, taskNum, operatorId);
            }
        } finally {
            WorkspaceContextHolder.clear();
        }
    }

    private void runAllRows(EvalTask task, String taskNum, String operatorId) {
        List<EvalDatasetRowVO> rows = evalDatasetQueryService.listRows(
                task.getDatasetNum(), task.getDatasetVersion(), task.getWorkspaceNum());
        List<GraderBindingSnapshot> bindings = parseBindings(task);
        Map<String, List<EvalDatasetRowVO>> groups = groupBySessionKey(rows);
        int total = rows.size();
        int passed = 0;
        int failed = 0;
        for (List<EvalDatasetRowVO> group : groups.values()) {
            group.sort(Comparator.comparing(EvalDatasetRowVO::getRowIndex));
            String priorTurns = "";
            for (EvalDatasetRowVO rowVo : group) {
                if (isCancelled(taskNum)) {
                    return;
                }
                RowOutcome outcome = processRow(task, taskNum, rowVo, bindings, priorTurns, operatorId, false);
                priorTurns = outcome.nextPriorTurns;
                if (outcome.passed) {
                    passed++;
                } else if (outcome.failed) {
                    failed++;
                }
                if (outcome.abortTask) {
                    return;
                }
            }
        }
        finishIfNotCancelled(taskNum, total, passed, failed, operatorId);
    }

    private void rerunFailedItems(EvalTask task, String taskNum, String operatorId) {
        List<EvalTaskItemVO> rerunnable = evalTaskQueryService.listRerunnableItems(taskNum, task.getWorkspaceNum());
        if (CollUtil.isEmpty(rerunnable)) {
            finishIfNotCancelled(taskNum, task.getTotalCount(), task.getPassedCount(), task.getFailedCount(), operatorId);
            return;
        }
        List<GraderBindingSnapshot> bindings = parseBindings(task);
        int passed = task.getPassedCount() == null ? 0 : task.getPassedCount();
        int failed = task.getFailedCount() == null ? 0 : task.getFailedCount();
        int total = task.getTotalCount() == null ? 0 : task.getTotalCount();
        Map<Integer, EvalDatasetRowVO> rowByIndex = evalDatasetQueryService.listRows(
                        task.getDatasetNum(), task.getDatasetVersion(), task.getWorkspaceNum())
                .stream()
                .collect(Collectors.toMap(EvalDatasetRowVO::getRowIndex, r -> r, (a, b) -> a));
        for (EvalTaskItemVO itemVo : rerunnable) {
            if (isCancelled(taskNum)) {
                return;
            }
            EvalDatasetRowVO rowVo = rowByIndex.get(itemVo.getRowIndex());
            if (rowVo == null) {
                continue;
            }
            boolean wasPassed = Boolean.TRUE.equals(itemVo.getOverallPass());
            if (wasPassed) {
                passed--;
            } else {
                failed--;
            }
            RowOutcome outcome = processRow(task, taskNum, rowVo, bindings, "", operatorId, true);
            if (outcome.passed) {
                passed++;
            } else if (outcome.failed) {
                failed++;
            }
            if (outcome.abortTask) {
                return;
            }
        }
        finishIfNotCancelled(taskNum, total, passed, failed, operatorId);
    }

    private RowOutcome processRow(EvalTask task, String taskNum, EvalDatasetRowVO rowVo,
                                  List<GraderBindingSnapshot> bindings, String priorTurns,
                                  String operatorId, boolean rerunExisting) {
        Map<String, Object> row = parseRow(rowVo.getDataJson());
        EvalTaskItem item;
        if (rerunExisting) {
            item = findExistingItem(taskNum, rowVo.getRowIndex());
            if (item == null) {
                item = evalTaskItemFactory.create(taskNum, rowVo.getRowIndex(), rowVo.getDataJson());
                item.save(operatorId);
            }
        } else {
            item = evalTaskItemFactory.create(taskNum, rowVo.getRowIndex(), rowVo.getDataJson());
            item.save(operatorId);
        }
        long start = System.currentTimeMillis();
        try {
            String actual;
            Object trace;
            if (task.getBindMode() == BindMode.AGENT) {
                InvokeResult invoke = invokeAgent(task, row, priorTurns, operatorId);
                actual = invoke.output();
                trace = invoke.trace();
            } else {
                actual = str(row.get("output"));
                trace = Map.of("toolNames", List.of(), "actualOutputLength", actual == null ? 0 : actual.length());
            }
            long latency = System.currentTimeMillis() - start;
            String traceJson = JSON.toJSONString(trace);
            List<ScoreResult> scores = graderEngine.evaluateAll(bindings, row, actual, trace);
            boolean overall = scores.stream().allMatch(ScoreResult::isPassed);
            List<EvalItemScore> scoreVos = toDomainScores(scores);
            if (overall) {
                item.markPassed(actual, traceJson, latency, scoreVos, operatorId);
                return new RowOutcome(true, false, false, extendPriorTurns(priorTurns, row, actual));
            }
            item.markFailed(actual, traceJson, latency, "评估未全部通过", scoreVos, operatorId);
            return new RowOutcome(false, true, false, extendPriorTurns(priorTurns, row, actual));
        } catch (Exception ex) {
            long latency = System.currentTimeMillis() - start;
            log.warn("eval item failed task={} row={}: {}", taskNum, rowVo.getRowIndex(), ex.getMessage());
            item.markError(ex.getMessage(), latency, operatorId);
            if (!continueOnError) {
                require(taskNum).fail(operatorId);
                return new RowOutcome(false, true, true, priorTurns);
            }
            return new RowOutcome(false, true, false, priorTurns);
        }
    }

    private EvalTaskItem findExistingItem(String taskNum, Integer rowIndex) {
        for (EvalTaskItemVO vo : evalTaskQueryService.listItems(taskNum, null)) {
            if (rowIndex.equals(vo.getRowIndex())) {
                return evalTaskItemFactory.createByNum(vo.getNum());
            }
        }
        return null;
    }

    private InvokeResult invokeAgent(EvalTask task, Map<String, Object> row, String priorTurns, String operatorId) {
        String input = str(row.get("input"));
        if (StrUtil.isBlank(input)) {
            input = str(row.get("query"));
        }
        if (StrUtil.isBlank(input)) {
            throw new IllegalArgumentException("行数据缺少 input 字段");
        }
        Map<String, Object> context = buildContext(row, priorTurns);
        SegmentAccumulator acc = new SegmentAccumulator();
        agentInvokeService.invokeStream(
                        task.getAgentNum(),
                        input,
                        null,
                        null,
                        operatorId,
                        task.getAgentVersionNum(),
                        context)
                .doOnNext(acc::accept)
                .blockLast(Duration.ofSeconds(invokeTimeoutSeconds));
        String text = acc.toContentText();
        return new InvokeResult(text == null ? "" : text, buildTraceSummary(acc, text));
    }

    private Map<String, Object> buildContext(Map<String, Object> row, String priorTurns) {
        Map<String, Object> context = new HashMap<>();
        Object ctx = row.get("context");
        if (ctx instanceof Map<?, ?> m) {
            for (Map.Entry<?, ?> e : m.entrySet()) {
                context.put(String.valueOf(e.getKey()), e.getValue());
            }
        } else if (ctx instanceof String s && StrUtil.isNotBlank(s)) {
            context.putAll(JSON.parseObject(s));
        }
        if (StrUtil.isNotBlank(priorTurns)) {
            context.put("priorTurns", priorTurns);
            Object existing = context.get("history");
            String merged = (existing == null ? "" : String.valueOf(existing) + "\n") + priorTurns;
            context.put("history", merged);
        }
        return context.isEmpty() ? null : context;
    }

    private Map<String, Object> buildTraceSummary(SegmentAccumulator acc, String actualOutput) {
        List<String> toolNames = new ArrayList<>();
        for (AssistantSegment seg : acc.getSegments()) {
            if ("tool_use".equals(seg.getKind()) && StrUtil.isNotBlank(seg.getToolName())) {
                toolNames.add(seg.getToolName());
            }
        }
        Map<String, Object> trace = new LinkedHashMap<>();
        trace.put("toolNames", toolNames);
        trace.put("actualOutputLength", actualOutput == null ? 0 : actualOutput.length());
        if (!toolNames.isEmpty()) {
            List<Map<String, String>> tools = new ArrayList<>();
            for (String n : toolNames) {
                tools.add(Map.of("name", n));
            }
            trace.put("tools", tools);
        }
        return trace;
    }

    private String extendPriorTurns(String prior, Map<String, Object> row, String actual) {
        String input = str(row.get("input"));
        if (StrUtil.isBlank(input)) {
            input = str(row.get("query"));
        }
        StringBuilder sb = new StringBuilder(prior == null ? "" : prior);
        if (StrUtil.isNotBlank(input)) {
            if (sb.length() > 0) {
                sb.append("\n");
            }
            sb.append("User: ").append(input);
        }
        if (StrUtil.isNotBlank(actual)) {
            sb.append("\nAssistant: ").append(actual);
        }
        return sb.toString();
    }

    private Map<String, List<EvalDatasetRowVO>> groupBySessionKey(List<EvalDatasetRowVO> rows) {
        Map<String, List<EvalDatasetRowVO>> groups = new LinkedHashMap<>();
        for (EvalDatasetRowVO row : rows) {
            Map<String, Object> data = parseRow(row.getDataJson());
            String key = str(data.get("sessionKey"));
            if (StrUtil.isBlank(key)) {
                key = "__single_" + row.getRowIndex();
            }
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(row);
        }
        return groups;
    }

    private List<GraderBindingSnapshot> parseBindings(EvalTask task) {
        return JSON.parseObject(task.getGraderBindingsJson(), new TypeReference<List<GraderBindingSnapshot>>() {});
    }

    private void finishIfNotCancelled(String taskNum, int total, int passed, int failed, String operatorId) {
        EvalTask finishTask = require(taskNum);
        if (finishTask.getStatus() == TaskStatus.CANCELLED) {
            return;
        }
        finishTask.finish(total, passed, failed, operatorId);
    }

    private boolean isCancelled(String taskNum) {
        EvalTask latest = evalTaskFactory.createByNum(taskNum);
        return latest != null && latest.getStatus() == TaskStatus.CANCELLED;
    }

    private void failTaskIfRunning(String taskNum, String operatorId) {
        try {
            EvalTask t = evalTaskFactory.createByNum(taskNum);
            if (t != null && t.getStatus() == TaskStatus.RUNNING) {
                t.fail(operatorId);
            }
        } catch (Exception inner) {
            log.error("EvalTaskWorker fail() also failed taskNum={}", taskNum, inner);
        }
    }

    private List<EvalItemScore> toDomainScores(List<ScoreResult> scores) {
        List<EvalItemScore> list = new ArrayList<>();
        for (ScoreResult s : scores) {
            list.add(EvalItemScore.builder()
                    .graderNum(s.getGraderNum())
                    .graderVersion(s.getGraderVersion())
                    .score(s.getScore())
                    .passed(s.isPassed())
                    .explanation(s.getExplanation())
                    .build());
        }
        return list;
    }

    private Map<String, Object> parseRow(String dataJson) {
        if (StrUtil.isBlank(dataJson)) {
            return new HashMap<>();
        }
        return JSON.parseObject(dataJson, new TypeReference<Map<String, Object>>() {});
    }

    private EvalTask require(String num) {
        EvalTask t = evalTaskFactory.createByNum(num);
        if (t == null) {
            throw new IllegalStateException("EvalTask not found: " + num);
        }
        return t;
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private record InvokeResult(String output, Object trace) {
    }

    private record RowOutcome(boolean passed, boolean failed, boolean abortTask, String nextPriorTurns) {
    }
}
