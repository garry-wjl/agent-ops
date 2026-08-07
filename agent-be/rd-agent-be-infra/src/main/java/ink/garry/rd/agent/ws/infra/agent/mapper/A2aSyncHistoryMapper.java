package ink.garry.rd.agent.ws.infra.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import ink.garry.rd.agent.ws.infra.agent.entity.A2aSyncHistoryEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * A2A 同步历史 Mapper（v2.6）。
 */
@Mapper
public interface A2aSyncHistoryMapper extends BaseMapper<A2aSyncHistoryEntity> {
}
