package ink.garry.rd.agent.ws.infra.sandbox.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import ink.garry.rd.agent.ws.infra.sandbox.entity.SandboxRuntimeInstanceEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 沙箱运行时实例 Mapper。
 */
@Mapper
public interface SandboxRuntimeInstanceMapper extends BaseMapper<SandboxRuntimeInstanceEntity> {
}
