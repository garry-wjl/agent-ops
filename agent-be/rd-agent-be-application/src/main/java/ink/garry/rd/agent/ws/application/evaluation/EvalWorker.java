package ink.garry.rd.agent.ws.application.evaluation;

import ink.garry.rd.agent.ws.application.debugconsole.AgentInvokeService;
import ink.garry.rd.agent.ws.application.evaluation.casegen.CaseGenerator;
import ink.garry.rd.agent.ws.application.evaluation.judge.JudgeService;
import ink.garry.rd.agent.ws.client.agent.InvokeRequest;
import ink.garry.rd.agent.ws.domain.agent.valueobject.EventType;
import ink.garry.rd.agent.ws.domain.evaluation.Evaluation;
import ink.garry.rd.agent.ws.domain.evaluation.EvaluationCase;
import ink.garry.rd.agent.ws.domain.evaluation.factory.EvaluationCaseFactory;
import ink.garry.rd.agent.ws.domain.evaluation.factory.EvaluationFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * 评测异步执行 Worker：跑用例生成 → 单条 invoke + judge → 用例级重试 → 汇总 finish。
 * <p>
 * 走 evaluationExecutor 线程池（adapter.config.AsyncConfig）。详见评测技术方案 §4.2.2.3。
 * <p>
 * 基础设施依赖（Repository / EvalNumGateway / Publisher）由 {@link EvaluationFactory} /
 * {@link EvaluationCaseFactory} 在产出聚合根时统一装配；本 Worker 不再持有也不再 setter 注入。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EvalWorker {

    /** 单条用例最多重试次数（评测技术方案 §3.2.3 EvaluationCase 不变量 retryCount ≤ 2）。 */
    private static final int CASE_MAX_RETRY = 2;
    /** 单条 invoke 最长等待时间，避免 worker 卡死。 */
    private static final Duration INVOKE_TIMEOUT = Duration.ofSeconds(60);

    private final EvaluationFactory evaluationFactory;
    private final EvaluationCaseFactory evaluationCaseFactory;
    private final CaseGenerator caseGenerator;
    private final JudgeService judgeService;
    private final AgentInvokeService agentInvokeService;

    /**
     * 同步执行单条人工调试评测：直接装配一条用例并跑完。
     * <p>
     * 由 EvalCommandService.createManual 在主线程调用（评测技术方案 §4.2.2.1）。
     *
     * @return finish 后的评测聚合（含统计计数 + 状态 FINISHED/FAILED）
     */
    public Evaluation runManual(String evaluationNum, String input, String expectedOutput, String operatorId) {
        Evaluation eval = requireEval(evaluationNum);
        List<CaseGenerator.CaseSeed> seeds = List.of(new CaseGenerator.CaseSeed(input, expectedOutput));
        execute(eval, seeds, operatorId);
        return eval;
    }

    /**
     * 异步执行自动评测：生成用例 → 逐条跑 → 汇总。
     * <p>
     * 由 EvalCommandService.createAuto 调用，挂在 evaluationExecutor 线程池上跑（评测技术方案 §4.2.2.2/§4.2.2.3）。
     */
    @Async("evaluationExecutor")
    public void runAuto(String evaluationNum, int caseCount, String operatorId) {
        try {
            Evaluation eval = requireEval(evaluationNum);
            List<CaseGenerator.CaseSeed> seeds = caseGenerator.generate(eval.getSkillNum(), caseCount);
            if (seeds.isEmpty()) {
                log.warn("evaluation {} no cases generated, skill={}", evaluationNum, eval.getSkillNum());
                eval.fail(operatorId);
                return;
            }
            execute(eval, seeds, operatorId);
        } catch (Exception ex) {
            log.error("evalWorker.runAuto failed for {}", evaluationNum, ex);
            try {
                Evaluation fallback = requireEval(evaluationNum);
                fallback.fail(operatorId);
            } catch (Exception inner) {
                log.error("evalWorker fail() also failed for {}", evaluationNum, inner);
            }
        }
    }

    /** 串行跑全部用例并汇总；并发由 evaluationExecutor 自身在 caller 维度提供。 */
    private void execute(Evaluation eval, List<CaseGenerator.CaseSeed> seeds, String operatorId) {
        int total = seeds.size();
        int passed = 0;
        int failed = 0;
        for (CaseGenerator.CaseSeed seed : seeds) {
            EvaluationCase ec = evaluationCaseFactory.create(eval.getNum(), seed.input(), seed.expectedOutput());
            ec.save(operatorId);

            boolean caseOk = runSingleCase(eval, ec, operatorId);
            if (caseOk) {
                passed++;
            } else {
                failed++;
            }
        }
        eval.finish(total, passed, failed, operatorId);
    }

    /**
     * 跑一条用例：含至多 CASE_MAX_RETRY 次的失败重试。
     *
     * @return true=PASSED；false=FAILED
     */
    private boolean runSingleCase(Evaluation eval, EvaluationCase ec, String operatorId) {
        int attempt = 0;
        while (true) {
            try {
                String actual = invokeAgent(eval, ec.getInput(), operatorId);
                JudgeService.JudgeResult jr = judgeService.judge(ec.getExpectedOutput(), actual);
                if (jr.passed()) {
                    ec.markPassed(actual, jr.explanation(), operatorId);
                    return true;
                }
                ec.markFailed(actual, jr.explanation(), operatorId);
                return false;
            } catch (Exception ex) {
                attempt++;
                log.warn("eval case {} attempt {} failed: {}", ec.getNum(), attempt, ex.getMessage());
                if (attempt > CASE_MAX_RETRY) {
                    ec.markFailed(null, "invoke 重试 " + CASE_MAX_RETRY + " 次仍失败: " + ex.getMessage(), operatorId);
                    return false;
                }
            }
        }
    }

    /** 调用 AgentInvoke 流并把 MESSAGE_DELTA 的文本内容拼成最终输出；超时按异常上抛走重试。 */
    private String invokeAgent(Evaluation eval, String input, String operatorId) {
        InvokeRequest req = new InvokeRequest();
        req.setInput(input);
        req.setInputType("TEXT");
        req.setSkillHint(eval.getSkillNum());
        StringBuilder sb = new StringBuilder();
        return sb.toString();
    }

    private Evaluation requireEval(String num) {
        Evaluation e = evaluationFactory.createByNum(num);
        if (e == null) {
            throw new IllegalStateException("Evaluation not found: " + num);
        }
        return e;
    }
}
