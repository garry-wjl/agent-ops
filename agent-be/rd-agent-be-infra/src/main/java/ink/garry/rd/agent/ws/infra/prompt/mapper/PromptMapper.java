package ink.garry.rd.agent.ws.infra.prompt.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import ink.garry.rd.agent.ws.infra.prompt.entity.PromptEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * Prompt 资产 Mapper（MyBatis Plus）。
 * <p>
 * 分页列表、{@code (workspace_num, prompt_key)} 唯一性预检、详情等条件查询，均由调用方
 * （{@code PromptQueryService} / {@code PromptCommandService}）通过 {@code LambdaQueryWrapper}
 * 构造，无需在此声明自定义 SQL。
 */
@Mapper
public interface PromptMapper extends BaseMapper<PromptEntity> {
}
