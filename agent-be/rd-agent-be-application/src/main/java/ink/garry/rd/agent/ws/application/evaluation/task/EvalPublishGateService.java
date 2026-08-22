package ink.garry.rd.agent.ws.application.evaluation.task;

import cn.hutool.core.lang.Assert;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import ink.garry.rd.agent.ws.client.common.BizCode;
import ink.garry.rd.agent.ws.client.evaluation.task.PublishGateCheckVO;
import ink.garry.rd.agent.ws.domain.evaluation.task.valueobject.TaskStatus;
import ink.garry.rd.agent.ws.facade.exception.BusinessException;
import ink.garry.rd.agent.ws.infra.evaluation.task.entity.EvalTaskEntity;
import ink.garry.rd.agent.ws.infra.evaluation.task.mapper.EvalTaskMapper;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Agent 发布门禁：要求存在 FINISHED 评测任务且通过率达标。
 */
@Service
public class EvalPublishGateService {

    @Value("${app.evaluation.publish-gate.enabled:false}")
    private boolean enabled;

    @Value("${app.evaluation.publish-gate.pass-rate-threshold:0.8}")
    private double passRateThreshold;

    @Resource
    private EvalTaskMapper evalTaskMapper;

    /**
     * 发布前校验；enabled=false 时 no-op。
     *
     * @param agentNum Agent 编号
     * @param agentVersionNum Agent 版本编号
     * @param workspaceNum 工作空间
     */
    public void checkAgentPublish(String agentNum, String agentVersionNum, String workspaceNum) {
        PublishGateCheckVO vo = evaluate(agentNum, agentVersionNum, workspaceNum);
        if (!vo.isEnabled()) {
            return;
        }
        if (!vo.isPassed()) {
            throw new BusinessException(BizCode.INVALID_PARAM.getCode(), vo.getMessage());
        }
    }

    /**
     * 预检发布门禁（供前端查询）。
     */
    public PublishGateCheckVO checkPublishGate(String agentNum, String agentVersionNum, String workspaceNum) {
        return evaluate(agentNum, agentVersionNum, workspaceNum);
    }

    private PublishGateCheckVO evaluate(String agentNum, String agentVersionNum, String workspaceNum) {
        Assert.notBlank(agentNum, "agentNum 不能为空");
        Assert.notBlank(agentVersionNum, "agentVersionNum 不能为空");
        PublishGateCheckVO vo = new PublishGateCheckVO();
        vo.setEnabled(enabled);
        vo.setRequiredPassRate(passRateThreshold);
        if (!enabled) {
            vo.setPassed(true);
            vo.setMessage("发布门禁未启用");
            return vo;
        }
        List<EvalTaskEntity> tasks = evalTaskMapper.selectList(Wrappers.<EvalTaskEntity>lambdaQuery()
                .eq(EvalTaskEntity::getWorkspaceNum, workspaceNum)
                .eq(EvalTaskEntity::getAgentNum, agentNum)
                .eq(EvalTaskEntity::getAgentVersionNum, agentVersionNum)
                .eq(EvalTaskEntity::getStatus, TaskStatus.FINISHED.name())
                .eq(EvalTaskEntity::getDeleted, 0)
                .orderByDesc(EvalTaskEntity::getUpdateTime));
        vo.setFinishedTaskCount(tasks.size());
        if (tasks.isEmpty()) {
            vo.setPassed(false);
            vo.setMessage("发布门禁：尚无 FINISHED 评测任务引用该 Agent 版本");
            return vo;
        }
        EvalTaskEntity best = tasks.get(0);
        int total = best.getTotalCount() == null ? 0 : best.getTotalCount();
        int passed = best.getPassedCount() == null ? 0 : best.getPassedCount();
        double rate = total == 0 ? 0d : (double) passed / total;
        vo.setPassRate(rate);
        vo.setPassed(rate >= passRateThreshold);
        vo.setMessage(vo.isPassed()
                ? "发布门禁通过，最近任务通过率 " + String.format("%.1f%%", rate * 100)
                : "发布门禁未通过：最近 FINISHED 任务通过率 "
                + String.format("%.1f%%", rate * 100) + "，要求 ≥ "
                + String.format("%.1f%%", passRateThreshold * 100));
        return vo;
    }
}
