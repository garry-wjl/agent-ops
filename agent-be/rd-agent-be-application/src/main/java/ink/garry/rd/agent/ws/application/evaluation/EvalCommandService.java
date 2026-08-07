package ink.garry.rd.agent.ws.application.evaluation;

import cn.hutool.core.util.StrUtil;
import ink.garry.rd.agent.ws.client.common.BizCode;
import ink.garry.rd.agent.ws.client.evaluation.AutoEvalParam;
import ink.garry.rd.agent.ws.client.evaluation.EvalRerunParam;
import ink.garry.rd.agent.ws.client.evaluation.EvaluationVO;
import ink.garry.rd.agent.ws.client.evaluation.ManualEvalParam;
import ink.garry.rd.agent.ws.domain.evaluation.Evaluation;
import ink.garry.rd.agent.ws.domain.evaluation.factory.EvaluationFactory;
import ink.garry.rd.agent.ws.facade.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 评测命令服务：编排评测任务的写用例（人工/自动创建、重跑、删除）。
 * <p>
 * 基础设施依赖（Repository / EvalNumGateway / Publisher）由 {@link EvaluationFactory}
 * 在产出聚合根时统一装配；本服务不再持有也不再 setter 注入。
 */
@Service
@RequiredArgsConstructor
public class EvalCommandService {

    /** AutoEvalParam.caseCount 默认值（评测技术方案 §3.2.2 业务规则）。 */
    private static final int DEFAULT_AUTO_CASE_COUNT = 10;
    /** AutoEvalParam.caseCount 上限。 */
    private static final int MAX_AUTO_CASE_COUNT = 50;

    private final EvaluationFactory evaluationFactory;
    private final EvalWorker evalWorker;

    /**
     * 人工调试评测：同步跑 1 条 input/expected → 立即返回最终结果。
     */
    @Transactional(rollbackFor = Exception.class)
    public EvaluationVO createManual(ManualEvalParam param, String operatorId) {
        if (StrUtil.isBlank(param.getInput())) {
            throw new BusinessException(BizCode.INVALID_PARAM.getCode(), "input 不能为空");
        }
        Evaluation eval = evaluationFactory.create(
                param.getName(), param.getAgentNum(), param.getAgentVersionNum(), param.getSkillNum(), operatorId);
        eval.save(operatorId);

        Evaluation finished = evalWorker.runManual(
                eval.getNum(), param.getInput(), param.getExpectedOutput(), operatorId);
        return toVO(finished);
    }

    /**
     * 自动评测：异步执行 → 立即返回 PENDING 任务编号；前端按 detail 接口轮询进度。
     */
    @Transactional(rollbackFor = Exception.class)
    public EvaluationVO createAuto(AutoEvalParam param, String operatorId) {
        int caseCount = param.getCaseCount() == null ? DEFAULT_AUTO_CASE_COUNT : param.getCaseCount();
        if (caseCount < 1 || caseCount > MAX_AUTO_CASE_COUNT) {
            throw new BusinessException(BizCode.INVALID_PARAM.getCode(),
                    "caseCount 必须在 [1," + MAX_AUTO_CASE_COUNT + "] 之间");
        }
        Evaluation eval = evaluationFactory.create(
                param.getName(), param.getAgentNum(), param.getAgentVersionNum(), param.getSkillNum(), operatorId);
        eval.save(operatorId);

        // 事务提交后再异步触发，保证 worker 能查到 evaluation 行
        EvaluationVO vo = toVO(eval);
        evalWorker.runAuto(eval.getNum(), caseCount, operatorId);
        return vo;
    }

    /**
     * 重跑：复用原配置 + 用当前在线 Agent 版本（agentVersionNum 留空表示评测最新）创建新评测。
     */
    @Transactional(rollbackFor = Exception.class)
    public EvaluationVO rerun(EvalRerunParam param, String operatorId) {
        Evaluation source = requireEval(param.getEvaluationNum());
        Evaluation eval = evaluationFactory.create(
                source.getName(), source.getAgentNum(), null, source.getSkillNum(), operatorId);
        eval.save(operatorId);
        evalWorker.runAuto(eval.getNum(), DEFAULT_AUTO_CASE_COUNT, operatorId);
        return toVO(eval);
    }

    /** 软删评测（不级联用例，由 evaluation_case.deleted 自然过期）。 */
    @Transactional(rollbackFor = Exception.class)
    public void delete(String evaluationNum, String operatorId) {
        Evaluation eval = requireEval(evaluationNum);
        eval.delete(operatorId);
    }

    private Evaluation requireEval(String num) {
        Evaluation eval = evaluationFactory.createByNum(num);
        if (eval == null) {
            throw new BusinessException(BizCode.NOT_FOUND.getCode(), "评测不存在 num=" + num);
        }
        return eval;
    }

    /** 转 VO：仅基础字段；详情走 EvalQueryService.detail。 */
    public EvaluationVO toVO(Evaluation e) {
        EvaluationVO vo = new EvaluationVO();
        vo.setNum(e.getNum());
        vo.setName(e.getName());
        vo.setAgentNum(e.getAgentNum());
        vo.setAgentVersionNum(e.getAgentVersionNum());
        vo.setSkillNum(e.getSkillNum());
        vo.setStatus(e.getStatus().name());
        vo.setTotalCaseCount(e.getTotalCaseCount());
        vo.setPassedCaseCount(e.getPassedCaseCount());
        vo.setFailedCaseCount(e.getFailedCaseCount());
        vo.setCreateTime(e.getCreateTime());
        vo.setUpdateTime(e.getUpdateTime());
        return vo;
    }
}
