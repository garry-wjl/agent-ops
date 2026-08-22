package ink.garry.rd.agent.ws.infra.evaluation.task.gateway;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import ink.garry.rd.agent.ws.domain.evaluation.task.gateway.EvalTaskGateway;
import ink.garry.rd.agent.ws.domain.evaluation.task.valueobject.EvalItemScore;
import ink.garry.rd.agent.ws.infra.evaluation.task.entity.EvalTaskItemScoreEntity;
import ink.garry.rd.agent.ws.infra.evaluation.task.mapper.EvalTaskItemScoreMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
public class EvalTaskGatewayImpl implements EvalTaskGateway {

    @Resource
    private EvalTaskItemScoreMapper evalTaskItemScoreMapper;

    @Override
    public void replaceItemScores(String taskItemNum, List<EvalItemScore> scores) {
        evalTaskItemScoreMapper.delete(new LambdaQueryWrapper<EvalTaskItemScoreEntity>()
                .eq(EvalTaskItemScoreEntity::getTaskItemNum, taskItemNum));
        if (scores == null) {
            return;
        }
        for (EvalItemScore s : scores) {
            EvalTaskItemScoreEntity e = new EvalTaskItemScoreEntity();
            e.setTaskItemNum(taskItemNum);
            e.setGraderNum(s.getGraderNum());
            e.setGraderVersion(s.getGraderVersion());
            e.setScore(s.getScore());
            e.setPassed(Boolean.TRUE.equals(s.getPassed()));
            e.setExplanation(s.getExplanation());
            e.setCreateTime(LocalDateTime.now());
            e.setUpdateTime(LocalDateTime.now());
            evalTaskItemScoreMapper.insert(e);
        }
    }
}
