package ink.garry.rd.agent.ws.infra.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import ink.garry.rd.agent.ws.infra.agent.entity.AgentUserMemoryEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户长期记忆 Mapper。
 */
@Mapper
public interface AgentUserMemoryMapper extends BaseMapper<AgentUserMemoryEntity> {
}
