package ink.garry.rd.agent.ws.infra.evaluation.dataset.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import ink.garry.rd.agent.ws.infra.evaluation.dataset.entity.EvalDatasetCaseGenJobEntity;
import org.apache.ibatis.annotations.Mapper;

/** 评测集自动生成 Case 任务 Mapper。 */
@Mapper
public interface EvalDatasetCaseGenJobMapper extends BaseMapper<EvalDatasetCaseGenJobEntity> {
}
