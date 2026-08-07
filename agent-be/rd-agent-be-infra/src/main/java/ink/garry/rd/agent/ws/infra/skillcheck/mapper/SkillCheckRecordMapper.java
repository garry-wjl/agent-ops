package ink.garry.rd.agent.ws.infra.skillcheck.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import ink.garry.rd.agent.ws.infra.skillcheck.entity.SkillCheckRecordEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * Skill 发布检测记录 Mapper（MyBatis Plus，v3.0 新增）。
 * <p>
 * 查询条件由调用方（{@code SkillQueryService} / {@code SkillCheckRecordRepositoryImpl}）通过
 * {@link com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper} 构造。
 */
@Mapper
public interface SkillCheckRecordMapper extends BaseMapper<SkillCheckRecordEntity> {
}
