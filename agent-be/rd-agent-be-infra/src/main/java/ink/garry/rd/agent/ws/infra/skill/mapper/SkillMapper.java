package ink.garry.rd.agent.ws.infra.skill.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import ink.garry.rd.agent.ws.infra.skill.entity.SkillEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * Skill 元信息 Mapper（MyBatis Plus）。
 * <p>
 * v2.10 收敛：所有查询条件由调用方（{@code SkillQueryService}）通过
 * {@link com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper} 构造，
 * 本接口不再声明任何自定义 {@code @Select} 方法 —— BaseMapper 的
 * selectOne / selectList / selectPage / selectCount 已足够覆盖现有读场景。
 */
@Mapper
public interface SkillMapper extends BaseMapper<SkillEntity> {
}
