package ink.garry.rd.agent.ws.infra.session.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import ink.garry.rd.agent.ws.infra.session.entity.InvocationTraceEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface InvocationTraceMapper extends BaseMapper<InvocationTraceEntity> {
}
