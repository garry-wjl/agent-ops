package ink.garry.rd.agent.ws.infra.evaluation.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import ink.garry.rd.agent.ws.infra.evaluation.entity.EvaluationCaseEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 评测执行用例 Mapper（MyBatis Plus）。
 */
@Mapper
public interface EvaluationCaseMapper extends BaseMapper<EvaluationCaseEntity> {
}
