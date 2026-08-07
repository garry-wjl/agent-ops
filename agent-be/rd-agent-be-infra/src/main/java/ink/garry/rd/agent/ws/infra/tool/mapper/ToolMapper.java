package ink.garry.rd.agent.ws.infra.tool.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import ink.garry.rd.agent.ws.infra.tool.entity.ToolEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 工具资产 Mapper（MyBatis Plus）。
 * <p>
 * 分页列表、{@code (workspace_num, name)} 唯一性预检、已发布挂载清单、按 {@code source_fc_tool_num}
 * 的引用检查等条件查询，均由调用方（{@code ToolQueryService} / {@code ToolCommandService}）通过
 * {@code LambdaQueryWrapper} 构造，无需在此声明自定义 SQL。复用数（reuseCount）统计扫
 * {@code agent.config_snapshot.mcpNums}，由 agent 领域 Mapper / QueryService 承担，不在本 Mapper。
 */
@Mapper
public interface ToolMapper extends BaseMapper<ToolEntity> {
}
