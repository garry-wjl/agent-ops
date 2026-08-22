package ink.garry.rd.agent.ws.infra.evaluation.grader.gateway;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import ink.garry.rd.agent.ws.domain.evaluation.grader.gateway.EvalGraderGateway;
import ink.garry.rd.agent.ws.domain.evaluation.task.valueobject.TaskStatus;
import ink.garry.rd.agent.ws.infra.evaluation.task.entity.EvalTaskEntity;
import ink.garry.rd.agent.ws.infra.evaluation.task.mapper.EvalTaskMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

@Component
public class EvalGraderGatewayImpl implements EvalGraderGateway {

    @Resource
    private EvalTaskMapper evalTaskMapper;

    @Override
    public int countRunningTasksByGrader(String graderNum) {
        // 绑定快照 JSON 粗匹配 graderNum
        Long c = evalTaskMapper.selectCount(new LambdaQueryWrapper<EvalTaskEntity>()
                .eq(EvalTaskEntity::getStatus, TaskStatus.RUNNING.name())
                .like(EvalTaskEntity::getGraderBindingsJson, graderNum));
        return c == null ? 0 : c.intValue();
    }
}
