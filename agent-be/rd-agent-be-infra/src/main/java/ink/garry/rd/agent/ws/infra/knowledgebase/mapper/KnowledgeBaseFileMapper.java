package ink.garry.rd.agent.ws.infra.knowledgebase.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import ink.garry.rd.agent.ws.infra.knowledgebase.entity.KnowledgeBaseFileEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface KnowledgeBaseFileMapper extends BaseMapper<KnowledgeBaseFileEntity> {
}
