package ink.garry.rd.agent.ws.infra.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import ink.garry.rd.agent.ws.infra.agent.entity.AgentVersionEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AgentVersionMapper extends BaseMapper<AgentVersionEntity> {
}
