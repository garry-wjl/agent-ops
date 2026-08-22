package ink.garry.rd.agent.ws.infra.evaluation.task.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import ink.garry.rd.agent.ws.infra.evaluation.task.entity.EvalTaskEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface EvalTaskMapper extends BaseMapper<EvalTaskEntity> {
}
