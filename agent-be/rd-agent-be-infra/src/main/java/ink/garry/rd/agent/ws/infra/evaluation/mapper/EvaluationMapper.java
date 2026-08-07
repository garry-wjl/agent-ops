package ink.garry.rd.agent.ws.infra.evaluation.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import ink.garry.rd.agent.ws.infra.evaluation.entity.EvaluationEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 评测任务 Mapper（MyBatis Plus）。
 */
@Mapper
public interface EvaluationMapper extends BaseMapper<EvaluationEntity> {
}
