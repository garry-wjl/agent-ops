package ink.garry.rd.agent.ws.infra.model.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import ink.garry.rd.agent.ws.infra.model.entity.ModelEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 模型接入资源 Mapper（MyBatis Plus）。
 * <p>
 * 分页列表、{@code (workspace_num, model_id)} / {@code (workspace_num, name)} 唯一性预检等条件查询，
 * 均由调用方（{@code ModelQueryService} / {@code ModelRepositoryImpl}）通过
 * {@code LambdaQueryWrapper} 构造，无需在此声明自定义 SQL。
 */
@Mapper
public interface ModelMapper extends BaseMapper<ModelEntity> {
}
