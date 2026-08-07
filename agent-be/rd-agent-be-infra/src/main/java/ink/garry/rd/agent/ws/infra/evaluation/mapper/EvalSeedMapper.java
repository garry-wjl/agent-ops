package ink.garry.rd.agent.ws.infra.evaluation.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import ink.garry.rd.agent.ws.infra.evaluation.entity.EvalSeedEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 评测黄金集种子 Mapper（MyBatis Plus）。
 */
@Mapper
public interface EvalSeedMapper extends BaseMapper<EvalSeedEntity> {
}
