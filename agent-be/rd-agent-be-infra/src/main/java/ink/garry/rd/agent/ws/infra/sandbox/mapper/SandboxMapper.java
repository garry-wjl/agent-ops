package ink.garry.rd.agent.ws.infra.sandbox.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import ink.garry.rd.agent.ws.infra.sandbox.entity.SandboxEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 沙箱资产 Mapper（MyBatis Plus）。
 * <p>
 * 分页列表、{@code (workspace_num, name)} 唯一性预检、在线沙箱清单等条件查询，
 * 均由调用方（{@code SandboxQueryService} / {@code SandboxCommandService}）通过
 * {@code LambdaQueryWrapper} 构造，无需在此声明自定义 SQL。
 */
@Mapper
public interface SandboxMapper extends BaseMapper<SandboxEntity> {
}
