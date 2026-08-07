package ink.garry.rd.agent.ws.infra.skill.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import ink.garry.rd.agent.ws.infra.skill.entity.SkillResourceFileEntity;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * Skill 资源文件树 Mapper（MyBatis Plus，v3.0 新增）。
 * <p>
 * 属 skill 聚合的子表 Mapper，供 {@code SkillRepositoryImpl} / {@code SkillVersionRepositoryImpl}
 * 在 save / findByNum 时级联读写资源树（按 {@code owner_type + owner_num} 维度），以及
 * {@code AgentScopeSkillRepositoryAdapter} / 查询服务装载资源树。查询条件由调用方通过
 * {@link com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper} 构造。
 */
@Mapper
public interface SkillResourceFileMapper extends BaseMapper<SkillResourceFileEntity> {

    /**
     * 物理删除指定 owner 下的全部资源行（绕过 MyBatis-Plus 全局逻辑删除）。
     * <p>
     * 资源树语义为「整树全量替换、先删后插」，旧行必须<b>真正删除</b>，不能走逻辑删除（{@code UPDATE deleted=1}）——
     * 否则同 {@code (owner_type, owner_num, path)} 的历史软删行会与本次软删行在唯一键
     * {@code uq_srf_owner_path(owner_type, owner_num, path, deleted)} 上冲突。故用原生
     * {@code DELETE} 物理删，{@code SkillRepositoryImpl}/{@code SkillVersionRepositoryImpl}
     * 级联保存时调用本方法清空旧树后再批量插入。
     *
     * @param ownerType 归属类型（SKILL / VERSION）
     * @param ownerNum  归属业务编号
     * @return 删除行数
     */
    @Delete("DELETE FROM skill_resource_file WHERE owner_type = #{ownerType} AND owner_num = #{ownerNum}")
    int physicalDeleteByOwner(@Param("ownerType") String ownerType, @Param("ownerNum") String ownerNum);
}
