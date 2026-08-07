package ink.garry.rd.agent.ws.infra.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import ink.garry.rd.agent.ws.infra.agent.entity.AgentApiKeyEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * Agent 对外调用秘钥 Mapper（agent_api_key 表）。
 */
@Mapper
public interface AgentApiKeyMapper extends BaseMapper<AgentApiKeyEntity> {
}
