package ink.garry.rd.agent.ws.infra.skill.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import ink.garry.rd.agent.ws.infra.skill.entity.SkillVersionEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * SkillVersion 持久化 Mapper（MyBatis Plus）。
 * <p>
 * v2.10 收敛：所有查询条件由调用方（{@code SkillQueryService}）通过
 * {@link com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper} 构造，
 * 本接口不再声明自定义 {@code @Select} 方法。
 */
@Mapper
public interface SkillVersionMapper extends BaseMapper<SkillVersionEntity> {
}
